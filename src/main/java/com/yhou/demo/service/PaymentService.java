package com.yhou.demo.service;

import com.yhou.demo.dto.PaymentDTO;
import com.yhou.demo.dto.PaymentAllocationDTO;
import com.yhou.demo.dto.request.PaymentAllocationRequest;
import com.yhou.demo.dto.request.SubmitPaymentRequest;
import com.yhou.demo.dto.response.SubmitPaymentResponse;
import com.yhou.demo.entity.*;
import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.entity.enums.CreditTransactionType;
import com.yhou.demo.entity.enums.PaymentStatus;
import com.yhou.demo.exception.BusinessValidationException;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yhou.demo.constants.ApplicationConstants.CHARGE_SUCCEEDED;
import static com.yhou.demo.constants.ApplicationConstants.CHARGE_FAILED;
import static com.yhou.demo.constants.ApplicationConstants.OVERPAYMENT_MULTIPLIER;
import static com.yhou.demo.constants.ApplicationConstants.OVERPAYMENT_DESCRIPTION;

/**
 * Core payment processing service handling payment submissions, webhook events,
 * and patient credit management for the healthcare copay system.
 *
 * Features:
 * - Idempotent payment processing with duplicate request protection
 * - Support for partial, full, and overpayments with automatic credit handling
 * - Asynchronous webhook processing for payment status updates
 * - Patient credit management for overpayment scenarios
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PatientRepository patientRepository;
    private final PatientCreditRepository patientCreditRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final ProcessorService processorService;
    private final CopayService copayService;

    /**
     * Submits a payment with allocation across multiple copays.
     * Implements idempotency protection and handles overpayments by creating patient credits.
     *
     * @param patientId the patient making the payment
     * @param request payment details including allocations across copays
     * @param requestKey unique identifier for duplicate request prevention
     * @return payment response with ID and current status
     * @throws ResourceNotFoundException if patient, payment method, or copay not found
     * @throws BusinessValidationException if allocation amounts are invalid
     */
    @Transactional
    public SubmitPaymentResponse submitPayment(Long patientId, SubmitPaymentRequest request, String requestKey) {
        log.info("Submitting payment for patient: {} with request key: {}", patientId, requestKey);

        // Step 1: Idempotency check - return existing payment if duplicate request
        Optional<Payment> existingPayment = paymentRepository.findByRequestKey(requestKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            log.info("Duplicate request detected, returning existing payment: {}", payment.getId());
            return SubmitPaymentResponse.builder()
                    .paymentId(payment.getId())
                    .status(payment.getStatus())
                    .build();
        }

        // Step 2: Validate patient exists
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Step 3: Validate payment method belongs to patient and is active
        PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndPatientIdAndIsActiveTrue(
                        request.getPaymentMethodId(), patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method", request.getPaymentMethodId()));

        // Step 4: Validate copays exist and are payable
        List<Long> copayIds = request.getAllocations().stream()
                .map(PaymentAllocationRequest::getCopayId)
                .toList();

        List<Copay> copays = copayService.getPayableCopaysByIds(copayIds, patientId);
        if (copays.size() != copayIds.size()) {
            throw new ResourceNotFoundException("Copay", copayIds.toString());
        }

        // Step 5: Validate allocation amounts (supports partial, full, and overpayments)
        validateAllocationAmounts(request.getAllocations(), copays);

        // Step 6: Calculate total payment amount
        BigDecimal totalAmount = request.getAllocations().stream()
                .map(PaymentAllocationRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 7: Create payment record in PENDING status
        Payment payment = Payment.builder()
                .patient(patient)
                .paymentMethod(paymentMethod)
                .amount(totalAmount)
                .currency(request.getCurrency())
                .requestKey(requestKey)
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        // Step 8: Create lookup map for efficient copay access
        Map<Long, Copay> copayMap = copays.stream()
                .collect(Collectors.toMap(Copay::getId, c -> c));

        // Step 9: Process allocations and calculate overpayment excess
        BigDecimal totalExcess = BigDecimal.ZERO;

        for (PaymentAllocationRequest allocationRequest : request.getAllocations()) {
            Copay copay = Optional.ofNullable(copayMap.get(allocationRequest.getCopayId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Copay", allocationRequest.getCopayId()));

            BigDecimal allocationAmount = allocationRequest.getAmount();
            BigDecimal copayRemainingBalance = copay.getRemainingBalance();

            // Calculate actual allocation (capped at remaining balance) and excess
            BigDecimal actualAllocation = allocationAmount.min(copayRemainingBalance);
            BigDecimal excess = allocationAmount.subtract(actualAllocation);
            totalExcess = totalExcess.add(excess);

            // Save allocation record (only actual amount applied to copay)
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setCopay(copay);
            allocation.setAmount(actualAllocation);
            paymentAllocationRepository.save(allocation);
        }

        // Step 10: Convert overpayment excess to patient credit
        if (totalExcess.compareTo(BigDecimal.ZERO) > 0) {
            addCreditToPatient(patient, payment, totalExcess);
        }

        // Step 11: Submit to payment processor for async processing
        String processorChargeId = processorService.processPayment(payment);
        payment.setProcessorChargeId(processorChargeId);
        paymentRepository.save(payment);

        log.info("Payment created successfully: {} with processor charge ID: {}", payment.getId(), processorChargeId);

        return SubmitPaymentResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .build();
    }

    /**
     * Handles webhook events from the payment processor to update payment status.
     * Implements idempotency protection to handle duplicate webhook deliveries.
     *
     * @param type webhook event type (charge.succeeded or charge.failed)
     * @param processorChargeId unique processor charge identifier
     * @param amount payment amount from processor
     * @param failureCode failure reason if payment failed
     * @throws ResourceNotFoundException if payment not found
     */
    @Transactional
    public void handleWebhookEvent(String type, String processorChargeId, BigDecimal amount, String failureCode) {
        log.info("Processing webhook event: {} for charge: {}", type, processorChargeId);

        Payment payment = paymentRepository.findByProcessorChargeId(processorChargeId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", processorChargeId));

        // Create audit trail of webhook data
        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("type", type);
        payloadMap.put("amount", amount);
        payloadMap.put("failureCode", failureCode);

        // Idempotency check - prevent duplicate processing
        if (!PaymentStatus.PENDING.equals(payment.getStatus())) {
            log.warn("Duplicate Process: Payment {} is not in PENDING status, current status: {}", payment.getId(), payment.getStatus());
            return;
        }

        if (CHARGE_SUCCEEDED.equals(type)) {
            // Mark payment as succeeded and update copay statuses
            payment.setStatus(PaymentStatus.SUCCEEDED);
            updateCopayStatusesFromPayment(payment);
            log.info("Payment {} succeeded, updated copay statuses", payment.getId());

        } else if (CHARGE_FAILED.equals(type)) {
            // Mark payment as failed and reverse any credits
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode(failureCode);
            reverseCreditFromFailedPayment(payment);
            log.info("Payment {} failed with code: {}", payment.getId(), failureCode);
        }

        paymentRepository.save(payment);
    }

    /**
     * Validates payment allocation amounts to prevent abuse while allowing overpayments.
     * Business rules: amounts must be positive and within reasonable limits.
     */
    private void validateAllocationAmounts(List<PaymentAllocationRequest> allocations, List<Copay> copays) {
        for (PaymentAllocationRequest allocation : allocations) {
            Copay copay = copays.stream()
                    .filter(c -> c.getId().equals(allocation.getCopayId()))
                    .findFirst()
                    .orElseThrow();

            // Validate positive amount
            if (allocation.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessValidationException("AMOUNT_NEGATIVE", "Allocation amount must be positive");
            }

            // Prevent excessive overpayments - TODO: make limit configurable per business rules
            BigDecimal maxAllowedAmount = copay.getAmount().multiply(BigDecimal.valueOf(OVERPAYMENT_MULTIPLIER));
            if (allocation.getAmount().compareTo(maxAllowedAmount) > 0) {
                throw new BusinessValidationException("ALLOCATION_EXCESSIVE",
                        String.format("Allocation amount (%s) exceeds reasonable limit (%s) for copay ID: %d",
                                allocation.getAmount(), maxAllowedAmount, copay.getId()));
            }
        }
    }

    /**
     * Updates copay statuses and remaining balances after successful payment.
     * Marks copays as PAID or PARTIALLY_PAID based on remaining balance.
     */
    private void updateCopayStatusesFromPayment(Payment payment) {
        for (PaymentAllocation allocation : payment.getPaymentAllocations()) {
            Copay copay = allocation.getCopay();
            BigDecimal newRemainingBalance = copay.getRemainingBalance().subtract(allocation.getAmount());

            copay.setRemainingBalance(newRemainingBalance);

            if (newRemainingBalance.compareTo(BigDecimal.ZERO) == 0) {
                copay.setStatus(CopayStatus.PAID);
                log.info("Copay {} marked as PAID", copay.getId());
            } else if (newRemainingBalance.compareTo(copay.getAmount()) < 0) {
                copay.setStatus(CopayStatus.PARTIALLY_PAID);
                log.info("Copay {} marked as PARTIALLY_PAID, remaining: {}", copay.getId(), newRemainingBalance);
            }
        }
    }

    /**
     * Adds overpayment credit to patient account and creates audit transaction.
     * Creates patient credit record if it doesn't exist.
     */
    private void addCreditToPatient(Patient patient, Payment payment, BigDecimal creditAmount) {
        log.info("Adding credit of {} to patient {}", creditAmount, patient.getId());

        // Ensure patient has credit record
        PatientCredit patientCredit = patientCreditRepository.findByPatientId(patient.getId())
                .orElseGet(() -> {
                    PatientCredit newCredit = new PatientCredit();
                    newCredit.setPatient(patient);
                    newCredit.setAmount(BigDecimal.ZERO);
                    return patientCreditRepository.save(newCredit);
                });

        // Add credit to existing balance
        patientCredit.setAmount(patientCredit.getAmount().add(creditAmount));
        patientCreditRepository.save(patientCredit);

        // Create audit transaction record
        CreditTransaction transaction = new CreditTransaction();
        transaction.setPatient(patient);
        transaction.setPayment(payment);
        transaction.setAmount(creditAmount);
        transaction.setTransactionType(CreditTransactionType.OVERPAYMENT_CREDIT);
        transaction.setDescription(String.format(OVERPAYMENT_DESCRIPTION, payment.getId()));
        creditTransactionRepository.save(transaction);
    }

    /**
     * Reverses patient credits when payment fails.
     * Finds all credit transactions for the failed payment and reverses them.
     */
    private void reverseCreditFromFailedPayment(Payment payment) {
        List<CreditTransaction> creditTransactions = creditTransactionRepository.findByPaymentId(payment.getId());

        for (CreditTransaction transaction : creditTransactions) {
            if (transaction.getTransactionType() == CreditTransactionType.OVERPAYMENT_CREDIT) {
                // Reverse the credit amount
                PatientCredit patientCredit = patientCreditRepository.findByPatientId(transaction.getPatient().getId())
                        .orElseThrow();

                patientCredit.setAmount(patientCredit.getAmount().subtract(transaction.getAmount()));
                patientCreditRepository.save(patientCredit);

                log.info("Reversed credit of {} for failed payment {}", transaction.getAmount(), payment.getId());
            }
        }
    }

    /**
     * Converts Payment entity to DTO with nested allocation information.
     */
    private PaymentDTO convertToDTO(Payment payment) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setPatientId(payment.getPatient().getId());
        dto.setPaymentMethodId(payment.getPaymentMethod().getId());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setStatus(payment.getStatus());
        dto.setProcessorChargeId(payment.getProcessorChargeId());
        dto.setFailureCode(payment.getFailureCode());
        dto.setCreatedAt(payment.getCreatedAt());

        // Include allocation details if present
        if (payment.getPaymentAllocations() != null) {
            dto.setAllocations(payment.getPaymentAllocations().stream()
                    .map(this::convertAllocationToDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * Converts PaymentAllocation entity to DTO.
     */
    private PaymentAllocationDTO convertAllocationToDTO(PaymentAllocation allocation) {
        PaymentAllocationDTO dto = new PaymentAllocationDTO();
        dto.setId(allocation.getId());
        dto.setCopayId(allocation.getCopay().getId());
        dto.setAmount(allocation.getAmount());
        return dto;
    }
}