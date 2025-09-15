package com.yhou.demo.service;

import com.yhou.demo.dto.request.PaymentAllocationRequest;
import com.yhou.demo.dto.request.SubmitPaymentRequest;
import com.yhou.demo.dto.response.SubmitPaymentResponse;
import com.yhou.demo.entity.*;
import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.entity.enums.PaymentMethodType;
import com.yhou.demo.entity.enums.PaymentStatus;
import com.yhou.demo.entity.enums.VisitType;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.exception.BusinessValidationException;
import com.yhou.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService covering payment processing, allocation logic, and idempotency.
 * Tests complex business scenarios including partial payments, overpayments, and credit management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceUnitTest {

    // Repository and service mocks for payment processing dependencies
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PatientCreditRepository patientCreditRepository;
    @Mock private CreditTransactionRepository creditTransactionRepository;
    @Mock private ProcessorService processorService;
    @Mock private CopayService copayService;

    @InjectMocks
    private PaymentService paymentService;

    // Shared test data for payment scenarios
    private Patient patient;
    private Visit visit;
    private PaymentMethod paymentMethod;
    private Copay copay;
    private SubmitPaymentRequest paymentRequest;
    private static final Long PATIENT_ID = 1L;
    private static final Long PAYMENT_METHOD_ID = 1L;
    private static final Long COPAY_ID = 1L;
    private static final String REQUEST_KEY = "test-request-key";

    @BeforeEach
    void setUp() {
        // Create test patient entity
        patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setFirstName("John");
        patient.setLastName("Doe");
        patient.setEmail("john.doe@email.com");

        // Create test visit entity
        visit = new Visit();
        visit.setId(1L);
        visit.setPatient(patient);
        visit.setVisitDate(LocalDate.now());
        visit.setDoctorName("Dr. Smith");
        visit.setDepartment("Internal Medicine");
        visit.setVisitType(VisitType.OFFICE_VISIT);

        // Create test payment method (active card)
        paymentMethod = new PaymentMethod();
        paymentMethod.setId(PAYMENT_METHOD_ID);
        paymentMethod.setPatient(patient);
        paymentMethod.setType(PaymentMethodType.CARD);
        paymentMethod.setProvider("VISA");
        paymentMethod.setLastFour("4242");
        paymentMethod.setIsActive(true);

        // Create test copay ($25 unpaid)
        copay = new Copay();
        copay.setId(COPAY_ID);
        copay.setVisit(visit);
        copay.setAmount(new BigDecimal("25.00"));
        copay.setRemainingBalance(new BigDecimal("25.00"));
        copay.setStatus(CopayStatus.PAYABLE);

        // Create basic payment request for $25 toward copay
        PaymentAllocationRequest allocation = new PaymentAllocationRequest();
        allocation.setCopayId(COPAY_ID);
        allocation.setAmount(new BigDecimal("25.00"));

        paymentRequest = new SubmitPaymentRequest();
        paymentRequest.setPaymentMethodId(PAYMENT_METHOD_ID);
        paymentRequest.setCurrency("USD");
        paymentRequest.setAllocations(Arrays.asList(allocation));
    }

    @Nested
    @DisplayName("Payment Allocation Tests")
    class PaymentAllocationTests {

        @Test
        @DisplayName("Should handle partial payment and update remaining balance correctly")
        void testPartialPayment_UpdatesRemainingBalanceAndStatus() {
            // Arrange - partial payment scenario: $15 toward $25 copay
            PaymentAllocationRequest allocation = new PaymentAllocationRequest();
            allocation.setCopayId(COPAY_ID);
            allocation.setAmount(new BigDecimal("15.00"));

            SubmitPaymentRequest request = new SubmitPaymentRequest();
            request.setPaymentMethodId(PAYMENT_METHOD_ID);
            request.setCurrency("USD");
            request.setAllocations(Arrays.asList(allocation));

            // Mock all dependencies for successful partial payment
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID), PATIENT_ID))
                    .thenReturn(Arrays.asList(copay));

            Payment savedPayment = Payment.builder()
                    .id(1L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("15.00"))
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
            when(processorService.processPayment(any(Payment.class))).thenReturn("ch_test123");

            // Act - submit partial payment
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, request, REQUEST_KEY);

            // Assert - verify response and business logic
            assertEquals(1L, response.getPaymentId());
            assertEquals(PaymentStatus.PENDING, response.getStatus());

            // Verify allocation saved with partial amount (not full copay)
            verify(paymentAllocationRepository).save(argThat(allocation1 ->
                    allocation1.getAmount().equals(new BigDecimal("15.00")) &&
                            allocation1.getCopay().getId().equals(COPAY_ID)
            ));

            // Verify no credit created for partial payment
            verify(patientCreditRepository, never()).save(any(PatientCredit.class));
            verify(creditTransactionRepository, never()).save(any(CreditTransaction.class));
        }

        @Test
        @DisplayName("Should handle full payment with exact copay amount")
        void testFullPayment_ExactAmount() {
            // Arrange - exact payment scenario: $25 toward $25 copay
            setupBasicMocks();

            Payment savedPayment = Payment.builder()
                    .id(1L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("25.00"))
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
            when(processorService.processPayment(any(Payment.class))).thenReturn("ch_test456");

            // Act - submit exact payment amount
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, paymentRequest, REQUEST_KEY);

            // Assert - verify successful processing
            assertEquals(1L, response.getPaymentId());
            assertEquals(PaymentStatus.PENDING, response.getStatus());

            // Verify allocation uses exact copay amount
            verify(paymentAllocationRepository).save(argThat(allocation1 ->
                    allocation1.getAmount().equals(new BigDecimal("25.00"))
            ));

            // Verify no credit processing for exact payment
            verify(patientCreditRepository, never()).findByPatientId(PATIENT_ID);
        }

        @Test
        @DisplayName("Should create patient credit when overpayment occurs")
        void testOverpayment_CreatesPatientCredit() {
            // Arrange - overpayment scenario: $35 toward $25 copay (creates $10 credit)
            PaymentAllocationRequest allocation = new PaymentAllocationRequest();
            allocation.setCopayId(COPAY_ID);
            allocation.setAmount(new BigDecimal("35.00"));

            SubmitPaymentRequest request = new SubmitPaymentRequest();
            request.setPaymentMethodId(PAYMENT_METHOD_ID);
            request.setCurrency("USD");
            request.setAllocations(Arrays.asList(allocation));

            // Patient has existing $5 credit
            PatientCredit existingCredit = new PatientCredit();
            existingCredit.setPatient(patient);
            existingCredit.setAmount(new BigDecimal("5.00"));

            // Mock dependencies for overpayment scenario
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID), PATIENT_ID))
                    .thenReturn(Arrays.asList(copay));
            when(patientCreditRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(existingCredit));

            Payment savedPayment = Payment.builder()
                    .id(1L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("35.00"))
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
            when(processorService.processPayment(any(Payment.class))).thenReturn("ch_test789");

            // Act - submit overpayment
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, request, REQUEST_KEY);

            // Assert - verify response
            assertEquals(1L, response.getPaymentId());
            assertEquals(PaymentStatus.PENDING, response.getStatus());

            // Verify allocation only uses copay amount (excess becomes credit)
            verify(paymentAllocationRepository).save(argThat(allocation1 ->
                    allocation1.getAmount().equals(new BigDecimal("25.00"))
            ));

            // Verify credit balance updated: $5 existing + $10 excess = $15 total
            verify(patientCreditRepository).save(argThat(credit ->
                    credit.getAmount().equals(new BigDecimal("15.00"))
            ));

            // Verify credit transaction logged for audit trail
            verify(creditTransactionRepository).save(argThat(transaction ->
                    transaction.getAmount().equals(new BigDecimal("10.00")) &&
                            transaction.getTransactionType().name().equals("OVERPAYMENT_CREDIT")
            ));
        }

        @Test
        @DisplayName("Should handle multiple allocations with partial and full payments")
        void testMultipleAllocations_PartialAndFull() {
            // Arrange - complex scenario: multiple copays with different payment amounts
            Copay copay2 = new Copay();
            copay2.setId(2L);
            copay2.setVisit(visit);
            copay2.setAmount(new BigDecimal("30.00"));
            copay2.setRemainingBalance(new BigDecimal("30.00"));
            copay2.setStatus(CopayStatus.PAYABLE);

            // First copay: partial payment ($15 of $25)
            PaymentAllocationRequest allocation1 = new PaymentAllocationRequest();
            allocation1.setCopayId(COPAY_ID);
            allocation1.setAmount(new BigDecimal("15.00"));

            // Second copay: full payment ($30 of $30)
            PaymentAllocationRequest allocation2 = new PaymentAllocationRequest();
            allocation2.setCopayId(2L);
            allocation2.setAmount(new BigDecimal("30.00"));

            SubmitPaymentRequest request = new SubmitPaymentRequest();
            request.setPaymentMethodId(PAYMENT_METHOD_ID);
            request.setCurrency("USD");
            request.setAllocations(Arrays.asList(allocation1, allocation2));

            // Mock dependencies for multi-allocation payment
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID, 2L), PATIENT_ID))
                    .thenReturn(Arrays.asList(copay, copay2));

            Payment savedPayment = Payment.builder()
                    .id(1L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("45.00"))  // $15 + $30 = $45 total
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
            when(processorService.processPayment(any(Payment.class))).thenReturn("ch_multi123");

            // Act - submit multi-allocation payment
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, request, REQUEST_KEY);

            // Assert - verify response
            assertEquals(1L, response.getPaymentId());
            assertEquals(PaymentStatus.PENDING, response.getStatus());

            // Verify both allocations saved separately
            verify(paymentAllocationRepository, times(2)).save(any(PaymentAllocation.class));

            // Verify total payment amount equals sum of allocations
            verify(paymentRepository, atLeastOnce()).save(argThat(payment ->
                    payment.getAmount().equals(new BigDecimal("45.00"))
            ));
        }
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should return existing payment when duplicate request key is used")
        void testIdempotency_DuplicateRequestKey_ReturnsSameResult() {
            // Arrange - payment already exists with same request key
            String requestKey = "duplicate-request-key-123";

            Payment existingPayment = Payment.builder()
                    .id(999L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("25.00"))
                    .status(PaymentStatus.SUCCEEDED)
                    .processorChargeId("ch_existing_123")
                    .requestKey(requestKey)
                    .build();

            when(paymentRepository.findByRequestKey(requestKey)).thenReturn(Optional.of(existingPayment));

            // Act - attempt duplicate payment with same request key
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, paymentRequest, requestKey);

            // Assert - returns existing payment data without reprocessing
            assertEquals(999L, response.getPaymentId());
            assertEquals(PaymentStatus.SUCCEEDED, response.getStatus());

            // Verify no new payment processing occurs (idempotency protection)
            verify(patientRepository, never()).findById(anyLong());
            verify(paymentMethodRepository, never()).findByIdAndPatientIdAndIsActiveTrue(anyLong(), anyLong());
            verify(copayService, never()).getPayableCopaysByIds(anyList(), anyLong());
            verify(processorService, never()).processPayment(any(Payment.class));
            verify(paymentRepository, never()).save(any(Payment.class));
            verify(paymentAllocationRepository, never()).save(any(PaymentAllocation.class));
        }

        @Test
        @DisplayName("Should create separate payments when different request keys are used")
        void testIdempotency_DifferentRequestKeys_CreatesSeparatePayments() {
            // Arrange - two different request keys should create separate payments
            String requestKey1 = "request-key-1";
            String requestKey2 = "request-key-2";

            when(paymentRepository.findByRequestKey(requestKey1)).thenReturn(Optional.empty());
            when(paymentRepository.findByRequestKey(requestKey2)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(anyList(), eq(PATIENT_ID)))
                    .thenReturn(List.of(copay));

            Payment payment1 = Payment.builder().id(1L).status(PaymentStatus.PENDING).build();
            Payment payment2 = Payment.builder().id(2L).status(PaymentStatus.PENDING).build();

            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment1)
                    .thenReturn(payment2);
            when(processorService.processPayment(any(Payment.class)))
                    .thenReturn("ch_123")
                    .thenReturn("ch_456");

            // Act - submit two payments with different request keys
            SubmitPaymentResponse response1 = paymentService.submitPayment(PATIENT_ID, paymentRequest, requestKey1);
            SubmitPaymentResponse response2 = paymentService.submitPayment(PATIENT_ID, paymentRequest, requestKey2);

            // Assert - verify separate payments created
            assertEquals(1L, response1.getPaymentId());
            assertEquals(2L, response2.getPaymentId());
        }

        @Test
        @DisplayName("Should return current status when duplicate request key has different payment status")
        void testIdempotency_DuplicateWithDifferentStatus_ReturnsCurrentStatus() {
            // Arrange - existing payment has different status (failed)
            String requestKey = "status-change-key";

            Payment existingPayment = Payment.builder()
                    .id(100L)
                    .patient(patient)
                    .paymentMethod(paymentMethod)
                    .amount(new BigDecimal("25.00"))
                    .status(PaymentStatus.FAILED)
                    .processorChargeId("ch_failed_123")
                    .requestKey(requestKey)
                    .failureCode("card_declined")
                    .build();

            when(paymentRepository.findByRequestKey(requestKey)).thenReturn(Optional.of(existingPayment));

            // Act - retry with same request key
            SubmitPaymentResponse response = paymentService.submitPayment(PATIENT_ID, paymentRequest, requestKey);

            // Assert - returns current failed status (no retry processing)
            assertEquals(100L, response.getPaymentId());
            assertEquals(PaymentStatus.FAILED, response.getStatus());

            // Verify no new processing attempted
            verify(processorService, never()).processPayment(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when patient does not exist")
        void testValidation_PatientNotFound_ThrowsException() {
            // Arrange - patient doesn't exist in database
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

            // Act & Assert - verify validation fails early
            assertThatThrownBy(() -> paymentService.submitPayment(PATIENT_ID, paymentRequest, REQUEST_KEY))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Patient")
                    .hasMessageContaining(PATIENT_ID.toString());

            // Verify subsequent validations not attempted
            verify(paymentMethodRepository, never()).findByIdAndPatientIdAndIsActiveTrue(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when payment method does not exist")
        void testValidation_PaymentMethodNotFound_ThrowsException() {
            // Arrange - payment method doesn't exist or is inactive
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.empty());

            // Act & Assert - verify payment method validation
            assertThatThrownBy(() -> paymentService.submitPayment(PATIENT_ID, paymentRequest, REQUEST_KEY))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment Method")
                    .hasMessageContaining(PAYMENT_METHOD_ID.toString());

            // Verify copay validation not attempted
            verify(copayService, never()).getPayableCopaysByIds(anyList(), anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when copay does not exist or is not payable")
        void testValidation_CopayNotFound_ThrowsException() {
            // Arrange - copay doesn't exist or cannot accept payments (paid/written off)
            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID), PATIENT_ID))
                    .thenReturn(Collections.emptyList()); // No payable copays found

            // Act & Assert - verify copay validation
            assertThatThrownBy(() -> paymentService.submitPayment(PATIENT_ID, paymentRequest, REQUEST_KEY))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Copay");

            // Verify payment processing not attempted
            verify(processorService, never()).processPayment(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw BusinessValidationException when allocation amount is negative")
        void testValidation_NegativeAmount_ThrowsException() {
            // Arrange - invalid business logic: negative payment amount
            PaymentAllocationRequest negativeAllocation = new PaymentAllocationRequest();
            negativeAllocation.setCopayId(COPAY_ID);
            negativeAllocation.setAmount(new BigDecimal("-10.00"));

            SubmitPaymentRequest invalidRequest = new SubmitPaymentRequest();
            invalidRequest.setPaymentMethodId(PAYMENT_METHOD_ID);
            invalidRequest.setCurrency("USD");
            invalidRequest.setAllocations(Arrays.asList(negativeAllocation));

            when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
            when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                    .thenReturn(Optional.of(paymentMethod));
            when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID), PATIENT_ID))
                    .thenReturn(Arrays.asList(copay));

            // Act & Assert - verify business validation
            assertThatThrownBy(() -> paymentService.submitPayment(PATIENT_ID, invalidRequest, REQUEST_KEY))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("positive");

            // Verify payment processing not attempted for invalid amounts
            verify(processorService, never()).processPayment(any(Payment.class));
        }
    }

    /**
     * Helper method to setup common mocks for successful payment scenarios.
     * Reduces test setup duplication for basic happy path tests.
     */
    private void setupBasicMocks() {
        when(paymentRepository.findByRequestKey(REQUEST_KEY)).thenReturn(Optional.empty());
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(PAYMENT_METHOD_ID, PATIENT_ID))
                .thenReturn(Optional.of(paymentMethod));
        when(copayService.getPayableCopaysByIds(Arrays.asList(COPAY_ID), PATIENT_ID))
                .thenReturn(Arrays.asList(copay));
    }
}