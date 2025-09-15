package com.yhou.demo.service;

import com.yhou.demo.dto.CopayDTO;
import com.yhou.demo.entity.Copay;
import com.yhou.demo.entity.Patient;
import com.yhou.demo.entity.Visit;
import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.entity.enums.VisitType;
import com.yhou.demo.exception.InvalidInputException;
import com.yhou.demo.exception.ResourceNotFoundException;
import com.yhou.demo.repository.CopayRepository;
import com.yhou.demo.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CopayService business logic and data mapping.
 * Tests service layer behavior, validation, and entity-to-DTO conversion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CopayService Tests")
class CopayServiceUnitTest {

    @Mock
    private CopayRepository copayRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private CopayService copayService;

    // Test data setup
    private Patient testPatient;
    private Visit testVisit;
    private Copay testCopay1;
    private Copay testCopay2;
    private static final Long PATIENT_ID = 1L;
    private static final Long VISIT_ID = 1L;
    private static final Long COPAY_ID_1 = 1L;
    private static final Long COPAY_ID_2 = 2L;

    @BeforeEach
    void setUp() {
        // Create test patient entity
        testPatient = new Patient();
        testPatient.setId(PATIENT_ID);
        testPatient.setFirstName("John");
        testPatient.setLastName("Doe");
        testPatient.setEmail("john.doe@email.com");

        // Create test visit entity
        testVisit = new Visit();
        testVisit.setId(VISIT_ID);
        testVisit.setPatient(testPatient);
        testVisit.setVisitDate(LocalDate.of(2024, 1, 15));
        testVisit.setDoctorName("Dr. Smith");
        testVisit.setDepartment("Internal Medicine");
        testVisit.setVisitType(VisitType.OFFICE_VISIT);
        testVisit.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        // Create test copay entities with different payment states
        testCopay1 = new Copay();
        testCopay1.setId(COPAY_ID_1);
        testCopay1.setVisit(testVisit);
        testCopay1.setAmount(new BigDecimal("25.00"));
        testCopay1.setRemainingBalance(new BigDecimal("25.00"));  // Unpaid
        testCopay1.setStatus(CopayStatus.PAYABLE);
        testCopay1.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 30));

        testCopay2 = new Copay();
        testCopay2.setId(COPAY_ID_2);
        testCopay2.setVisit(testVisit);
        testCopay2.setAmount(new BigDecimal("45.00"));
        testCopay2.setRemainingBalance(new BigDecimal("20.00"));  // Partially paid
        testCopay2.setStatus(CopayStatus.PARTIALLY_PAID);
        testCopay2.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 45));
    }

    @Test
    @DisplayName("Should return all copays when no status filter is provided")
    void getCopays_NoStatusFilter_ReturnsAllCopays() {
        // Given - patient exists and has multiple copays
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null))
                .thenReturn(Arrays.asList(testCopay1, testCopay2));

        // When - retrieving copays without status filter
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, null);

        // Then - verify all copays returned with correct DTO mapping
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(COPAY_ID_1);
        assertThat(result.get(0).getAmount()).isEqualTo(new BigDecimal("25.00"));
        assertThat(result.get(0).getRemainingBalance()).isEqualTo(new BigDecimal("25.00"));
        assertThat(result.get(0).getStatus()).isEqualTo(CopayStatus.PAYABLE);
        assertThat(result.get(0).getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(result.get(0).getVisitId()).isEqualTo(VISIT_ID);
        assertThat(result.get(0).getDoctorName()).isEqualTo("Dr. Smith");
        assertThat(result.get(0).getDepartment()).isEqualTo("Internal Medicine");
        assertThat(result.get(0).getVisitType()).isEqualTo(VisitType.OFFICE_VISIT);

        verify(patientRepository).existsById(PATIENT_ID);
        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null);
    }

    @Test
    @DisplayName("Should return filtered copays when status filter is provided")
    void getCopays_WithStatusFilter_ReturnsFilteredCopays() {
        // Given - patient exists and service filters by status
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, CopayStatus.PAYABLE))
                .thenReturn(Collections.singletonList(testCopay1));

        // When - retrieving copays with status filter
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, "payable");

        // Then - verify only matching copays returned
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(COPAY_ID_1);
        assertThat(result.get(0).getStatus()).isEqualTo(CopayStatus.PAYABLE);

        verify(patientRepository).existsById(PATIENT_ID);
        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, CopayStatus.PAYABLE);
    }

    @Test
    @DisplayName("Should handle case insensitive status filter")
    void getCopays_CaseInsensitiveStatus_ReturnsFilteredCopays() {
        // Given - lowercase status input
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, CopayStatus.PARTIALLY_PAID))
                .thenReturn(Collections.singletonList(testCopay2));

        // When - using lowercase status parameter
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, "partially_paid");

        // Then - verify case insensitive conversion works
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(COPAY_ID_2);
        assertThat(result.get(0).getStatus()).isEqualTo(CopayStatus.PARTIALLY_PAID);

        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, CopayStatus.PARTIALLY_PAID);
    }

    @Test
    @DisplayName("Should ignore blank status filter")
    void getCopays_BlankStatus_ReturnsAllCopays() {
        // Given - blank/whitespace status parameter
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null))
                .thenReturn(Arrays.asList(testCopay1, testCopay2));

        // When - using whitespace-only status
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, "   ");

        // Then - verify blank status is treated as no filter
        assertThat(result).hasSize(2);
        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when patient does not exist")
    void getCopays_PatientNotExists_ThrowsResourceNotFoundException() {
        // Given - patient doesn't exist in database
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(false);

        // When & Then - verify exception thrown and repository not called
        assertThatThrownBy(() -> copayService.getCopays(PATIENT_ID, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("copays")
                .hasMessageContaining(PATIENT_ID.toString());

        verify(patientRepository).existsById(PATIENT_ID);
        verifyNoInteractions(copayRepository);
    }

    @Test
    @DisplayName("Should throw InvalidInputException for invalid status")
    void getCopays_InvalidStatus_ThrowsInvalidInputException() {
        // Given - patient exists but invalid status provided
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);

        // When & Then - verify validation exception for invalid enum value
        assertThatThrownBy(() -> copayService.getCopays(PATIENT_ID, "invalid_status"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("status")
                .hasMessageContaining("invalid_status");

        verify(patientRepository).existsById(PATIENT_ID);
        verifyNoInteractions(copayRepository);
    }

    @Test
    @DisplayName("Should return empty list when no copays found")
    void getCopays_NoCopaysFound_ReturnsEmptyList() {
        // Given - patient exists but has no copays
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null))
                .thenReturn(Collections.emptyList());

        // When - retrieving copays for patient with none
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, null);

        // Then - verify empty list returned
        assertThat(result).isEmpty();
        verify(patientRepository).existsById(PATIENT_ID);
        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null);
    }

    @Test
    @DisplayName("Should return payable and partially paid copays")
    void getPayableCopaysByIds_ValidIds_ReturnsPayableCopays() {
        // Given - copay IDs that can receive payments
        List<Long> copayIds = Arrays.asList(COPAY_ID_1, COPAY_ID_2);
        List<CopayStatus> expectedStatuses = Arrays.asList(CopayStatus.PAYABLE, CopayStatus.PARTIALLY_PAID);
        when(copayRepository.findCopaysByIdsAndPatientIdAndStatusIn(copayIds, PATIENT_ID, expectedStatuses))
                .thenReturn(Arrays.asList(testCopay1, testCopay2));

        // When - retrieving copays that can accept payments
        List<Copay> result = copayService.getPayableCopaysByIds(copayIds, PATIENT_ID);

        // Then - verify only payment-eligible copays returned
        assertThat(result).hasSize(2);
        assertThat(result).contains(testCopay1, testCopay2);
        verify(copayRepository).findCopaysByIdsAndPatientIdAndStatusIn(copayIds, PATIENT_ID, expectedStatuses);
    }

    @Test
    @DisplayName("Should return empty list when no payable copays found")
    void getPayableCopaysByIds_NoPayableCopays_ReturnsEmptyList() {
        // Given - copays that cannot receive payments (fully paid or written off)
        List<Long> copayIds = Arrays.asList(COPAY_ID_1);
        List<CopayStatus> expectedStatuses = Arrays.asList(CopayStatus.PAYABLE, CopayStatus.PARTIALLY_PAID);
        when(copayRepository.findCopaysByIdsAndPatientIdAndStatusIn(copayIds, PATIENT_ID, expectedStatuses))
                .thenReturn(Collections.emptyList());

        // When - no payment-eligible copays found
        List<Copay> result = copayService.getPayableCopaysByIds(copayIds, PATIENT_ID);

        // Then - verify empty list returned
        assertThat(result).isEmpty();
        verify(copayRepository).findCopaysByIdsAndPatientIdAndStatusIn(copayIds, PATIENT_ID, expectedStatuses);
    }

    @Test
    @DisplayName("Should mark copays as paid")
    void markCopaysAsPaid_ValidIds_UpdatesStatus() {
        // Given - copays that need status update after full payment
        List<Long> copayIds = Arrays.asList(COPAY_ID_1, COPAY_ID_2);
        when(copayRepository.updateCopayStatus(copayIds, CopayStatus.PAID)).thenReturn(2);

        // When - marking copays as fully paid
        copayService.markCopaysAsPaid(copayIds);

        // Then - verify repository update called
        verify(copayRepository).updateCopayStatus(copayIds, CopayStatus.PAID);
    }

    @Test
    @DisplayName("Should handle empty copay IDs list for marking as paid")
    void markCopaysAsPaid_EmptyList_CallsRepository() {
        // Given - empty list of copay IDs
        List<Long> copayIds = Collections.emptyList();
        when(copayRepository.updateCopayStatus(copayIds, CopayStatus.PAID)).thenReturn(0);

        // When - calling with empty list
        copayService.markCopaysAsPaid(copayIds);

        // Then - verify repository still called (handles gracefully)
        verify(copayRepository).updateCopayStatus(copayIds, CopayStatus.PAID);
    }

    @Test
    @DisplayName("Should correctly convert Copay entity to DTO")
    void convertToDTO_ValidCopay_ReturnsCorrectDTO() {
        // Given - copay entity with complete visit and patient data
        when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
        when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null))
                .thenReturn(Collections.singletonList(testCopay1));

        // When - converting entity to DTO through service call
        List<CopayDTO> result = copayService.getCopays(PATIENT_ID, null);

        // Then - verify all entity fields properly mapped to DTO
        assertThat(result).hasSize(1);
        CopayDTO dto = result.get(0);

        assertThat(dto.getId()).isEqualTo(testCopay1.getId());
        assertThat(dto.getVisitId()).isEqualTo(testCopay1.getVisit().getId());
        assertThat(dto.getPatientId()).isEqualTo(testCopay1.getVisit().getPatient().getId());
        assertThat(dto.getAmount()).isEqualTo(testCopay1.getAmount());
        assertThat(dto.getRemainingBalance()).isEqualTo(testCopay1.getRemainingBalance());
        assertThat(dto.getStatus()).isEqualTo(testCopay1.getStatus());
        assertThat(dto.getVisitDate()).isEqualTo(testCopay1.getVisit().getVisitDate());
        assertThat(dto.getDoctorName()).isEqualTo(testCopay1.getVisit().getDoctorName());
        assertThat(dto.getDepartment()).isEqualTo(testCopay1.getVisit().getDepartment());
        assertThat(dto.getVisitType()).isEqualTo(testCopay1.getVisit().getVisitType());
        assertThat(dto.getCreatedAt()).isEqualTo(testCopay1.getCreatedAt());

        verify(patientRepository).existsById(PATIENT_ID);
        verify(copayRepository).findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, null);
    }

    @Test
    @DisplayName("Should handle various copay statuses correctly")
    void getCopays_VariousStatuses_HandlesAllStatusTypes() {
        // Test all valid enum values for comprehensive coverage
        String[] validStatuses = {"PAYABLE", "PAID", "PARTIALLY_PAID", "WRITE_OFF"};

        for (String status : validStatuses) {
            // Given - each valid status type
            when(patientRepository.existsById(PATIENT_ID)).thenReturn(true);
            CopayStatus expectedStatus = CopayStatus.valueOf(status);
            when(copayRepository.findCopaysByPatientIdAndOptionalStatus(PATIENT_ID, expectedStatus))
                    .thenReturn(Collections.emptyList());

            // When & Then - verify all enum values handled correctly
            assertThatCode(() -> copayService.getCopays(PATIENT_ID, status.toLowerCase()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should handle null copay IDs gracefully")
    void getPayableCopaysByIds_NullIds_HandlesGracefully() {
        // Given - null copay IDs list (edge case)
        when(copayRepository.findCopaysByIdsAndPatientIdAndStatusIn(
                eq(null), eq(PATIENT_ID), any()))
                .thenReturn(Collections.emptyList());

        // When - calling with null IDs
        List<Copay> result = copayService.getPayableCopaysByIds(null, PATIENT_ID);

        // Then - verify graceful handling of null input
        assertThat(result).isEmpty();
    }
}