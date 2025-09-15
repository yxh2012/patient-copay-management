package com.yhou.demo.controller;

import com.yhou.demo.dto.CopayDTO;
import com.yhou.demo.dto.response.CopayAISummaryResponse;
import com.yhou.demo.dto.response.ListCopaysResponse;
import com.yhou.demo.dto.request.SubmitPaymentRequest;
import com.yhou.demo.dto.response.SubmitPaymentResponse;
import com.yhou.demo.entity.Patient;
import com.yhou.demo.exception.InvalidParameterException;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.repository.PatientRepository;
import com.yhou.demo.service.CopayService;
import com.yhou.demo.service.PaymentService;
import com.yhou.demo.service.SimpleAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for patient copay and payment operations.
 * All endpoints are patient-scoped and require a valid patient ID.
 */
@RestController
@RequestMapping("/api/v1/patients/{id}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Patient Operations", description = "APIs for patient copay and payment operations")
public class PatientController {

    /** Allowed query parameters for copay filtering */
    private static final Set<String> ALLOWED_PARAMS = Set.of("status");

    private final CopayService copayService;
    private final PaymentService paymentService;
    private final SimpleAiService simpleAiService;
    private final PatientRepository patientRepository;

    /**
     * Retrieves copays for a patient with optional status filtering.
     *
     * @param id the patient identifier
     * @param status optional status filter (payable, paid, write_off)
     * @param allParams all query parameters for validation
     * @return response containing filtered copays
     * @throws ResourceNotFoundException if patient does not exist
     * @throws InvalidParameterException if unsupported query parameters provided
     */
    @GetMapping("/copays")
    @Operation(
            summary = "List copays for a patient",
            description = "Retrieve all copays for a patient, optionally filtered by status (e.g., payable, paid, write_off)"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved copays")
    @ApiResponse(responseCode = "400", description = "Invalid request (invalid status or unsupported query parameter)")
    @ApiResponse(responseCode = "404", description = "Patient not found")
    @ApiResponse(responseCode = "422", description = "Business validation failed")
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
    public ResponseEntity<ListCopaysResponse> listCopays(
            @Parameter(description = "Patient ID") @PathVariable Long id,
            @Parameter(description = "Filter by status (e.g., payable, paid, write_off)")
            @RequestParam(required = false) String status,
            @RequestParam Map<String, String> allParams) {

        log.info("Listing copays for patient: {} with status filter: {}", id, status);

        validateQueryParams(allParams);

        List<CopayDTO> copays = copayService.getCopays(id, status);
        return ResponseEntity.ok(new ListCopaysResponse(copays));
    }

    /**
     * Generates AI-powered copay summary and recommendations for a patient.
     *
     * @param id the patient identifier
     * @return AI-generated copay analysis with financial insights
     * @throws ResourceNotFoundException if patient does not exist
     */
    @GetMapping("/copayAISummary")
    @Operation(
            summary = "Generate AI-powered copay summary for a patient",
            description = "Get an intelligent summary of patient's copay status, payment patterns, and actionable recommendations"
    )
    @ApiResponse(responseCode = "200", description = "Successfully generated copay summary")
    @ApiResponse(responseCode = "404", description = "Patient not found")
    @ApiResponse(responseCode = "500", description = "AI service unavailable or unexpected error")
    public ResponseEntity<CopayAISummaryResponse> getCopayAISummary(
            @Parameter(description = "Patient ID") @PathVariable Long id) {

        log.info("Generating AI copay summary for patient: {}", id);

        // Validate patient exists
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));

        // Get all copays for AI analysis
        List<CopayDTO> copays = copayService.getCopays(id, null);

        // Generate AI summary
        String patientName = patient.getFirstName() + " " + patient.getLastName();
        CopayAISummaryResponse response = simpleAiService.generateCopaySummary(copays, patientName);

        // Build response with patient ID
        CopayAISummaryResponse finalResponse = CopayAISummaryResponse.builder()
                .patientId(id)
                .patientName(response.getPatientName())
                .generatedAt(response.getGeneratedAt())
                .accountStatus(response.getAccountStatus())
                .financialOverview(response.getFinancialOverview())
                .recommendations(response.getRecommendations())
                .insights(response.getInsights())
                .summarySource(response.getSummarySource())
                .build();

        return ResponseEntity.ok(finalResponse);
    }

    /**
     * Processes payment submission for a patient with idempotency support.
     *
     * @param id the patient identifier
     * @param duplicateRequestKey optional idempotency key to prevent duplicate processing
     * @param request payment details and copay allocations
     * @return payment processing results
     * @throws ResourceNotFoundException if patient does not exist
     */
    @PostMapping("/payments")
    @Operation(
            summary = "Submit a payment for a patient",
            description = "Submit a payment that can be allocated across one or more copays"
    )
    @ApiResponse(responseCode = "200", description = "Payment submitted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data")
    @ApiResponse(responseCode = "404", description = "Patient or payment method not found")
    @ApiResponse(responseCode = "409", description = "Duplicate request")
    public ResponseEntity<SubmitPaymentResponse> submitPayment(
            @Parameter(description = "Patient ID")
            @PathVariable Long id,
            @Parameter(description = "Idempotency key to prevent duplicate charges")
            @RequestHeader(value = "Duplicate-Request-Key", required = false) String duplicateRequestKey,
            @Valid @RequestBody SubmitPaymentRequest request) {

        // Generate idempotency key if not provided
        String requestKey = duplicateRequestKey != null ? duplicateRequestKey : UUID.randomUUID().toString();

        log.info("Submitting payment for patient: {} with request key: {}", id, requestKey);

        SubmitPaymentResponse response = paymentService.submitPayment(id, request, requestKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Validates that only whitelisted query parameters are present.
     *
     * @param allParams map of all query parameters from request
     * @throws InvalidParameterException if unsupported parameters found
     */
    private void validateQueryParams(Map<String, String> allParams) {
        List<String> invalidParams = allParams.keySet().stream()
                .filter(param -> !ALLOWED_PARAMS.contains(param))
                .toList();

        if (!invalidParams.isEmpty()) {
            throw new InvalidParameterException(invalidParams.get(0));
        }
    }
}