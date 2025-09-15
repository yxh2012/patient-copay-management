package com.yhou.demo.controller;

import com.yhou.demo.dto.request.WebhookRequest;
import com.yhou.demo.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for handling payment processor webhook events.
 * Processes charge status updates from external payment providers.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Payment processor webhook endpoints")
public class WebhookController {

    private final PaymentService paymentService;

    /**
     * Handles payment processor webhook events for charge status updates.
     *
     * @param request webhook payload containing charge details and status
     * @return empty response indicating successful processing
     * @throws RuntimeException if webhook processing fails
     */
    @PostMapping("/processor")
    @Operation(
            summary = "Receive payment processor callbacks",
            description = "Handle charge.succeeded and charge.failed events from payment processor"
    )
    @ApiResponse(responseCode = "200", description = "Webhook processed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid webhook data")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    public ResponseEntity<Void> handleProcessorWebhook(@Valid @RequestBody WebhookRequest request) {
        log.info("Received webhook: {} for processor charge: {}", request.getType(), request.getProcessorChargeId());

        try {
            // Delegate webhook processing to payment service
            paymentService.handleWebhookEvent(
                    request.getType(),
                    request.getProcessorChargeId(),
                    request.getAmount(),
                    request.getFailureCode()
            );

            log.info("Webhook processed successfully for charge: {}", request.getProcessorChargeId());
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing webhook for charge: " + request.getProcessorChargeId(), e);
            // Let global exception handler deal with it
            throw e;
        }
    }
}