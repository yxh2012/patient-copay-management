package com.yhou.demo.service;

import com.yhou.demo.dto.CopayDTO;
import com.yhou.demo.entity.Copay;
import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.exception.InvalidInputException;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.repository.CopayRepository;
import com.yhou.demo.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing patient copay operations including retrieval,
 * validation, and status updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CopayService {

    private final CopayRepository copayRepository;
    private final PatientRepository patientRepository;

    /**
     * Retrieves copays for a patient with optional status filtering.
     *
     * @param patientId the patient identifier
     * @param status optional status filter (payable, paid, write_off, partially_paid)
     * @return list of copay DTOs with visit and patient information
     * @throws ResourceNotFoundException if patient does not exist
     * @throws InvalidInputException if status value is invalid
     */
    @Transactional(readOnly = true)
    public List<CopayDTO> getCopays(Long patientId, String status) {
        log.info("Fetching copays for patient: {} with status filter: {}", patientId, status);

        // Validate patient exists before proceeding
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("copays", patientId);
        }

        // Parse and validate status filter if provided
        CopayStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = CopayStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("status", status);
            }
        }

        List<Copay> copays = copayRepository.findCopaysByPatientIdAndOptionalStatus(patientId, filter);

        return copays.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves copays that can accept payments (PAYABLE or PARTIALLY_PAID status).
     * Used during payment processing to validate payment allocations.
     *
     * @param copayIds list of copay identifiers to retrieve
     * @param patientId patient identifier for security validation
     * @return list of copays that can receive payments
     */
    @Transactional(readOnly = true)
    public List<Copay> getPayableCopaysByIds(List<Long> copayIds, Long patientId) {
        // Include PARTIALLY_PAID copays as they can still receive payments
        return copayRepository.findCopaysByIdsAndPatientIdAndStatusIn(
                copayIds,
                patientId,
                List.of(CopayStatus.PAYABLE, CopayStatus.PARTIALLY_PAID)
        );
    }

    /**
     * Marks copays as fully paid. Used after successful payment processing
     * when copay remaining balances reach zero.
     *
     * @param copayIds list of copay identifiers to mark as paid
     */
    @Transactional
    public void markCopaysAsPaid(List<Long> copayIds) {
        log.info("Marking copays as paid: {}", copayIds);
        copayRepository.updateCopayStatus(copayIds, CopayStatus.PAID);
    }

    /**
     * Converts Copay entity to DTO with flattened visit and patient information.
     *
     * @param copay the copay entity to convert
     * @return copay DTO with visit details included
     */
    private CopayDTO convertToDTO(Copay copay) {
        CopayDTO dto = new CopayDTO();
        dto.setId(copay.getId());
        dto.setVisitId(copay.getVisit().getId());
        dto.setPatientId(copay.getVisit().getPatient().getId());
        dto.setAmount(copay.getAmount());
        dto.setRemainingBalance(copay.getRemainingBalance());
        dto.setStatus(copay.getStatus());

        // Include visit information for UI display
        dto.setVisitDate(copay.getVisit().getVisitDate());
        dto.setDoctorName(copay.getVisit().getDoctorName());
        dto.setDepartment(copay.getVisit().getDepartment());
        dto.setVisitType(copay.getVisit().getVisitType());
        dto.setCreatedAt(copay.getCreatedAt());

        return dto;
    }
}