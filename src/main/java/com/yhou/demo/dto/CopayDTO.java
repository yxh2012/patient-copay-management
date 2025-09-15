package com.yhou.demo.dto;

import com.yhou.demo.entity.enums.CopayStatus;
import com.yhou.demo.entity.enums.VisitType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing patient copay information with visit details and payment status.
 */
@Data
public class CopayDTO {
    private Long id;
    private Long visitId;
    private Long patientId;     // visit.patient.id
    private BigDecimal amount;
    private BigDecimal remainingBalance;
    private CopayStatus status;
    private LocalDate visitDate; // visit.visitDate
    private String doctorName;   // visit.doctorName
    private String department;   // visit.department
    private VisitType visitType; // visit.visitType (enum directly)
    private LocalDateTime createdAt;

    // Helper methods
    public BigDecimal getPaidAmount() {
        return amount.subtract(remainingBalance);
    }

    public boolean isFullyPaid() {
        return remainingBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPartiallyPaid() {
        return remainingBalance.compareTo(BigDecimal.ZERO) > 0 &&
                remainingBalance.compareTo(amount) < 0;
    }
}