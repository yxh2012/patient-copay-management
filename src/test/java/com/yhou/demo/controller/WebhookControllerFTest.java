package com.yhou.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhou.demo.dto.request.WebhookRequest;
import com.yhou.demo.exception.GlobalExceptionHandler;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static com.yhou.demo.constants.ApplicationConstants.CHARGE_SUCCEEDED;
import static com.yhou.demo.constants.ApplicationConstants.CHARGE_FAILED;

/**
 * Functional tests for WebhookController payment processor callbacks.
 * Tests webhook processing, validation, and error handling scenarios.
 */
@WebMvcTest(WebhookController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("WebhookController Functional Tests")
class WebhookControllerFTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private static final String BASE_URL = "/api/v1/webhooks";
    private static final String PROCESSOR_CHARGE_ID = "ch_test123";
    private static final BigDecimal AMOUNT = new BigDecimal("25.00");

    @BeforeEach
    void setUp() {
        // Reset mock to clean state for each test
        reset(paymentService);
    }

    @Test
    @DisplayName("Should process successful payment webhook with 200 status")
    void handleWebhook_SuccessfulPayment_ReturnsSuccess() throws Exception {
        // Given - successful payment webhook from processor
        WebhookRequest request = createSuccessfulWebhookRequest();

        doNothing().when(paymentService).handleWebhookEvent(
                eq(CHARGE_SUCCEEDED),
                eq(PROCESSOR_CHARGE_ID),
                eq(AMOUNT),
                isNull()
        );

        // When & Then - verify successful processing and delegation to service
        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(paymentService).handleWebhookEvent(
                CHARGE_SUCCEEDED,
                PROCESSOR_CHARGE_ID,
                AMOUNT,
                null
        );
    }

    @Test
    @DisplayName("Should process failed payment webhook with 200 status")
    void handleWebhook_FailedPayment_ReturnsSuccess() throws Exception {
        // Given - failed payment webhook with failure code
        WebhookRequest request = createFailedWebhookRequest();

        doNothing().when(paymentService).handleWebhookEvent(
                eq(CHARGE_FAILED),
                eq(PROCESSOR_CHARGE_ID),
                eq(AMOUNT),
                eq("card_declined")
        );

        // When & Then - verify failed payment processing
        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(paymentService).handleWebhookEvent(
                CHARGE_FAILED,
                PROCESSOR_CHARGE_ID,
                AMOUNT,
                "card_declined"
        );
    }

    @Test
    @DisplayName("Should return 404 when payment not found")
    void handleWebhook_PaymentNotFound_ReturnsNotFound() throws Exception {
        // Given - service throws ResourceNotFoundException for unknown payment
        WebhookRequest request = createSuccessfulWebhookRequest();

        doThrow(new ResourceNotFoundException("Payment", PROCESSOR_CHARGE_ID))
                .when(paymentService).handleWebhookEvent(
                        anyString(), anyString(), any(BigDecimal.class), any()
                );

        // When & Then - verify 404 response with proper error structure
        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        verify(paymentService).handleWebhookEvent(
                CHARGE_SUCCEEDED,
                PROCESSOR_CHARGE_ID,
                AMOUNT,
                null
        );
    }

    @Test
    @DisplayName("Should return 400 when request body is invalid")
    void handleWebhook_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Given - webhook with missing required fields
        WebhookRequest invalidRequest = new WebhookRequest();

        // When & Then - verify validation error response
        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        // Service should not be called for invalid requests
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("Should handle duplicate webhook deliveries gracefully")
    void handleWebhook_DuplicateDelivery_ReturnsSuccess() throws Exception {
        // Given - same webhook payload sent multiple times (common in webhook systems)
        WebhookRequest request = createSuccessfulWebhookRequest();

        doNothing().when(paymentService).handleWebhookEvent(
                anyString(), anyString(), any(BigDecimal.class), any()
        );

        // When - send identical webhook twice
        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/processor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then - both requests processed (idempotency handled at service layer)
        verify(paymentService, times(2)).handleWebhookEvent(
                CHARGE_SUCCEEDED,
                PROCESSOR_CHARGE_ID,
                AMOUNT,
                null
        );
    }

    /**
     * Creates a successful payment webhook request for testing.
     */
    private WebhookRequest createSuccessfulWebhookRequest() {
        WebhookRequest request = new WebhookRequest();
        request.setType(CHARGE_SUCCEEDED);
        request.setProcessorChargeId(PROCESSOR_CHARGE_ID);
        request.setAmount(AMOUNT);
        return request;
    }

    /**
     * Creates a failed payment webhook request with failure code for testing.
     */
    private WebhookRequest createFailedWebhookRequest() {
        WebhookRequest request = new WebhookRequest();
        request.setType(CHARGE_FAILED);
        request.setProcessorChargeId(PROCESSOR_CHARGE_ID);
        request.setAmount(AMOUNT);
        request.setFailureCode("card_declined");
        return request;
    }
}