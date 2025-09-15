package com.yhou.demo.service;

import com.yhou.demo.entity.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.yhou.demo.constants.ApplicationConstants.CHARGE_SUCCEEDED;
import static com.yhou.demo.constants.ApplicationConstants.CHARGE_FAILED;

/**
 * Simulates a third-party payment processor for development and testing.
 *
 * This service mimics the behavior of real payment processors like Stripe or Square
 * by providing asynchronous payment processing with webhook callbacks. It generates
 * random success/failure outcomes to test the full payment workflow including
 * error handling and retry scenarios.
 *
 * NOTE: This is a simulation for development only - replace with actual
 * payment processor integration for production use.
 */
@Service
@Slf4j
public class ProcessorService {

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    /**
     * Initializes the processor service with HTTP client and webhook configuration.
     *
     * @param restTemplate HTTP client for making webhook callbacks
     * @param webhookUrl configurable webhook endpoint URL for callbacks
     */
    public ProcessorService(RestTemplate restTemplate,
                            @Value("${app.webhook.url:http://localhost:8080/api/v1/webhooks/processor}") String webhookUrl) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Initiates asynchronous payment processing and returns a processor charge ID.
     *
     * This method simulates the behavior of real payment processors that:
     * 1. Accept payment requests synchronously
     * 2. Return a charge ID immediately
     * 3. Process payments asynchronously in the background
     * 4. Send webhook notifications when processing completes
     *
     * @param payment the payment entity to process
     * @return unique processor charge identifier for tracking
     */
    public String processPayment(Payment payment) {
        // Generate unique processor charge ID (mimics real processor behavior)
        String processorChargeId = "ch_" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Processing payment {} with processor, charge ID: {}", payment.getId(), processorChargeId);

        // Simulate async processing - real processors return immediately and process in background
        CompletableFuture.runAsync(() -> simulateProcessorCallback(processorChargeId, payment.getAmount()));

        return processorChargeId;
    }

    /**
     * Simulates the payment processor's background processing and webhook delivery.
     *
     * This method replicates real-world payment processor behavior:
     * - Variable processing delays (2-5 seconds)
     * - Random success/failure outcomes (90% success rate)
     * - Webhook callbacks with charge status updates
     * - Different failure codes for testing error scenarios
     */
    private void simulateProcessorCallback(String processorChargeId, BigDecimal amount) {
        try {
            // Simulate realistic processing delay (payment processors aren't instant)
            Thread.sleep(2000 + (long)(Math.random() * 3000)); // 2-5 seconds

            // Simulate payment outcome with realistic success rate
            boolean success = Math.random() < 0.8; // 80% success rate

            String eventType = success ? CHARGE_SUCCEEDED : CHARGE_FAILED;
            String failureCode = success ? null : getRandomFailureCode();

            // Build webhook payload matching real processor webhook format
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("type", eventType);
            webhookPayload.put("processorChargeId", processorChargeId);
            webhookPayload.put("amount", amount);
            if (failureCode != null) {
                webhookPayload.put("failureCode", failureCode);
            }

            // Send webhook callback to our application
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(webhookPayload, headers);

            log.info("Sending webhook callback: {} for charge: {}", eventType, processorChargeId);
            restTemplate.postForEntity(webhookUrl, request, String.class);

            // Uncomment to test duplicate webhook handling
            // Thread.sleep(1000);
            // log.info("Re-sending webhook callback (duplicate) for charge: {}", processorChargeId);
            // restTemplate.postForEntity(webhookUrl, request, String.class);

        } catch (Exception e) {
            log.error("Error in processor simulation for charge: {}", processorChargeId, e);
        }
    }

    /**
     * Returns a random failure code to simulate different payment failure scenarios.
     *
     * These failure codes match common real-world payment processor error types
     * and help test different error handling paths in the application.
     *
     * @return random failure code string
     */
    private String getRandomFailureCode() {
        // Common payment failure scenarios for comprehensive testing
        String[] failureCodes = {
                "card_declined",      // Most common - insufficient funds, blocked card, etc.
                "insufficient_funds", // Specific insufficient funds case
                "card_expired",       // Expired payment method
                "processing_error",   // Generic processor error
                "network_error"       // Communication failure
        };
        return failureCodes[(int)(Math.random() * failureCodes.length)];
    }
}