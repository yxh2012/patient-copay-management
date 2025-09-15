package com.yhou.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhou.demo.dto.CopayDTO;
import com.yhou.demo.dto.request.PaymentAllocationRequest;
import com.yhou.demo.dto.request.SubmitPaymentRequest;
import com.yhou.demo.dto.response.CopayAISummaryResponse;
import com.yhou.demo.dto.response.SubmitPaymentResponse;
import com.yhou.demo.entity.Patient;
import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.entity.enums.PaymentStatus;
import com.yhou.demo.entity.enums.VisitType;
import com.yhou.demo.repository.PatientRepository;
import com.yhou.demo.service.CopayService;
import com.yhou.demo.service.PaymentService;
import com.yhou.demo.service.SimpleAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Functional tests for PatientController endpoints using MockMvc.
 * Tests controller layer behavior, request/response mapping, and validation.
 */
@WebMvcTest(PatientController.class)
@SpringJUnitConfig
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientController Functional Tests")
class PatientControllerFTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock dependencies - using TestConfiguration to avoid @MockBean conflicts
    private CopayService copayService;
    private PaymentService paymentService;
    private SimpleAiService simpleAiService;
    private PatientRepository patientRepository;

    private static final Long PATIENT_ID = 1L;
    private static final String BASE_URL = "/api/v1/patients/" + PATIENT_ID;

    /**
     * Test configuration to provide mocked beans for the controller.
     * Using @Primary to override any existing beans in the context.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public CopayService copayService() {
            return mock(CopayService.class);
        }

        @Bean
        @Primary
        public PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        @Primary
        public SimpleAiService simpleAiService() {
            return mock(SimpleAiService.class);
        }

        @Bean
        @Primary
        public PatientRepository patientRepository() {
            return mock(PatientRepository.class);
        }
    }

    @Autowired
    public void setMocks(CopayService copayService,
                         PaymentService paymentService,
                         SimpleAiService simpleAiService,
                         PatientRepository patientRepository) {
        this.copayService = copayService;
        this.paymentService = paymentService;
        this.simpleAiService = simpleAiService;
        this.patientRepository = patientRepository;
    }

    @BeforeEach
    void setUp() {
        // Reset all mocks to clean state before each test
        reset(copayService, paymentService, simpleAiService, patientRepository);
    }

    @Nested
    @DisplayName("GET /copays - List Copays")
    class ListCopaysTests {

        @Test
        @DisplayName("Should return copays successfully with 200 status")
        void listCopays_ValidRequest_ReturnsSuccess() throws Exception {
            // Given - mock service returns sample copays
            CopayDTO copay1 = createCopayDTO(1L, new BigDecimal("25.00"), CopayStatus.PAYABLE);
            CopayDTO copay2 = createCopayDTO(2L, new BigDecimal("45.00"), CopayStatus.PAID);

            when(copayService.getCopays(PATIENT_ID, null))
                    .thenReturn(Arrays.asList(copay1, copay2));

            // When & Then - verify response structure and calculated totals
            mockMvc.perform(get(BASE_URL + "/copays"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.copays", hasSize(2)))
                    .andExpect(jsonPath("$.copays[0].id", is(1)))
                    .andExpect(jsonPath("$.copays[0].amount", is(25.00)))
                    .andExpect(jsonPath("$.copays[0].status", is("PAYABLE")))
                    .andExpect(jsonPath("$.copays[1].id", is(2)))
                    .andExpect(jsonPath("$.copays[1].amount", is(45.00)))
                    .andExpect(jsonPath("$.copays[1].status", is("PAID")))
                    .andExpect(jsonPath("$.totalAmount", is(70.00)))
                    .andExpect(jsonPath("$.count", is(2)));
        }

        @Test
        @DisplayName("Should filter copays by status when status parameter provided")
        void listCopays_WithStatusFilter_ReturnsFilteredCopays() throws Exception {
            // Given - service filters by status
            CopayDTO payableCopay = createCopayDTO(1L, new BigDecimal("25.00"), CopayStatus.PAYABLE);

            when(copayService.getCopays(PATIENT_ID, "payable"))
                    .thenReturn(Collections.singletonList(payableCopay));

            // When & Then - verify filtering works
            mockMvc.perform(get(BASE_URL + "/copays")
                            .param("status", "payable"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.copays", hasSize(1)))
                    .andExpect(jsonPath("$.copays[0].status", is("PAYABLE")));
        }

        @Test
        @DisplayName("Should return 400 when invalid query parameter provided")
        void listCopays_InvalidQueryParam_ReturnsBadRequest() throws Exception {
            // When & Then - test parameter validation
            mockMvc.perform(get(BASE_URL + "/copays")
                            .param("invalidParam", "value"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return empty list when no copays found")
        void listCopays_NoCopaysFound_ReturnsEmptyList() throws Exception {
            // Given - service returns empty list
            when(copayService.getCopays(PATIENT_ID, null))
                    .thenReturn(Collections.emptyList());

            // When & Then - verify empty response structure
            mockMvc.perform(get(BASE_URL + "/copays"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.copays", hasSize(0)))
                    .andExpect(jsonPath("$.count", is(0)))
                    .andExpect(jsonPath("$.totalAmount", is(0)));
        }
    }

    @Nested
    @DisplayName("POST /payments - Submit Payment")
    class SubmitPaymentTests {

        @Test
        @DisplayName("Should submit payment successfully with 200 status")
        void submitPayment_ValidRequest_ReturnsSuccess() throws Exception {
            // Given - valid payment request and expected response
            SubmitPaymentRequest request = createPaymentRequest();
            SubmitPaymentResponse expectedResponse = SubmitPaymentResponse.builder()
                    .paymentId(123L)
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentService.submitPayment(eq(PATIENT_ID), any(SubmitPaymentRequest.class), anyString()))
                    .thenReturn(expectedResponse);

            // When & Then - verify successful payment submission
            mockMvc.perform(post(BASE_URL + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(123)))
                    .andExpect(jsonPath("$.status", is("PENDING")));
        }

        @Test
        @DisplayName("Should handle idempotency with duplicate request key")
        void submitPayment_WithDuplicateRequestKey_ReturnsSuccess() throws Exception {
            // Given - request with explicit idempotency key
            SubmitPaymentRequest request = createPaymentRequest();
            String duplicateKey = "duplicate-key-123";

            SubmitPaymentResponse expectedResponse = SubmitPaymentResponse.builder()
                    .paymentId(999L)
                    .status(PaymentStatus.SUCCEEDED)
                    .build();

            when(paymentService.submitPayment(eq(PATIENT_ID), any(SubmitPaymentRequest.class), eq(duplicateKey)))
                    .thenReturn(expectedResponse);

            // When & Then - verify idempotency header is processed
            mockMvc.perform(post(BASE_URL + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Duplicate-Request-Key", duplicateKey)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(999)))
                    .andExpect(jsonPath("$.status", is("SUCCEEDED")));
        }

        @Test
        @DisplayName("Should return 400 when request body is invalid")
        void submitPayment_InvalidRequest_ReturnsBadRequest() throws Exception {
            // Given - invalid request missing required fields
            SubmitPaymentRequest invalidRequest = new SubmitPaymentRequest();

            // When & Then - verify validation triggers
            mockMvc.perform(post(BASE_URL + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when allocation amount is invalid")
        void submitPayment_InvalidAllocationAmount_ReturnsBadRequest() throws Exception {
            // Given - request with amount below minimum threshold
            PaymentAllocationRequest invalidAllocation = new PaymentAllocationRequest();
            invalidAllocation.setCopayId(1L);
            invalidAllocation.setAmount(new BigDecimal("0.50")); // Below $1.00 minimum

            SubmitPaymentRequest request = new SubmitPaymentRequest();
            request.setPaymentMethodId(1L);
            request.setCurrency("USD");
            request.setAllocations(Arrays.asList(invalidAllocation));

            // When & Then - verify amount validation
            mockMvc.perform(post(BASE_URL + "/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("amount")));
        }
    }

    @Nested
    @DisplayName("GET /copayAISummary - AI Summary")
    class CopayAISummaryTests {

        @Test
        @DisplayName("Should return AI summary successfully with 200 status")
        void getCopayAISummary_ValidRequest_ReturnsSuccess() throws Exception {
            // Given - patient exists and AI service returns summary
            Patient patient = new Patient();
            patient.setId(PATIENT_ID);
            patient.setFirstName("John");
            patient.setLastName("Doe");

            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

            CopayAISummaryResponse expectedResponse = CopayAISummaryResponse.builder()
                    .patientId(PATIENT_ID)
                    .patientName("John Doe")
                    .generatedAt(LocalDateTime.now())
                    .accountStatus("Some outstanding balances")
                    .financialOverview(CopayAISummaryResponse.FinancialOverview.builder()
                            .outstandingBalance("$145.00")
                            .totalAmount("$145.00")
                            .totalCopays(3)
                            .paidCopays(0)
                            .unpaidCopays(3)
                            .partiallyPaidCopays(0)
                            .build())
                    .recommendations(Arrays.asList(
                            "Consider setting up a payment plan",
                            "Prioritize oldest unpaid copays first"
                    ))
                    .insights(Arrays.asList(
                            "Higher than average copay amounts detected",
                            "Patient visits multiple departments"
                    ))
                    .summarySource("SYSTEM")
                    .build();

            when(simpleAiService.generateCopaySummary(any(), eq("John Doe")))
                    .thenReturn(expectedResponse);
            when(copayService.getCopays(PATIENT_ID, null))
                    .thenReturn(Collections.emptyList());

            // When & Then - verify AI summary structure
            mockMvc.perform(get(BASE_URL + "/copayAISummary"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.patientId", is(1)))
                    .andExpect(jsonPath("$.patientName", is("John Doe")))
                    .andExpect(jsonPath("$.accountStatus", is("Some outstanding balances")))
                    .andExpect(jsonPath("$.financialOverview.outstandingBalance", is("$145.00")))
                    .andExpect(jsonPath("$.financialOverview.totalCopays", is(3)))
                    .andExpect(jsonPath("$.recommendations", hasSize(2)))
                    .andExpect(jsonPath("$.insights", hasSize(2)))
                    .andExpect(jsonPath("$.summarySource", is("SYSTEM")));
        }

        @Test
        @DisplayName("Should return 404 when patient not found")
        void getCopayAISummary_PatientNotFound_ReturnsNotFound() throws Exception {
            // Given - patient doesn't exist
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

            // When & Then - verify 404 response
            mockMvc.perform(get(BASE_URL + "/copayAISummary"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("Patient")))
                    .andExpect(jsonPath("$.message", containsString(PATIENT_ID.toString())));
        }
    }

    /**
     * Creates a test CopayDTO with specified values for testing.
     */
    private CopayDTO createCopayDTO(Long id, BigDecimal amount, CopayStatus status) {
        CopayDTO copay = new CopayDTO();
        copay.setId(id);
        copay.setVisitId(1L);
        copay.setPatientId(PATIENT_ID);
        copay.setAmount(amount);
        copay.setRemainingBalance(status == CopayStatus.PAID ? BigDecimal.ZERO : amount);
        copay.setStatus(status);
        copay.setVisitDate(LocalDate.now());
        copay.setDoctorName("Dr. Smith");
        copay.setDepartment("Internal Medicine");
        copay.setVisitType(VisitType.OFFICE_VISIT);
        copay.setCreatedAt(LocalDateTime.now());
        return copay;
    }

    /**
     * Creates a valid payment request for testing payment submission.
     */
    private SubmitPaymentRequest createPaymentRequest() {
        PaymentAllocationRequest allocation = new PaymentAllocationRequest();
        allocation.setCopayId(1L);
        allocation.setAmount(new BigDecimal("25.00"));

        SubmitPaymentRequest request = new SubmitPaymentRequest();
        request.setPaymentMethodId(1L);
        request.setCurrency("USD");
        request.setAllocations(Arrays.asList(allocation));
        return request;
    }
}