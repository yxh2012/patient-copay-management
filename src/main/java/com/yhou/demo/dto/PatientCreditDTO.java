package com.yhou.demo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing patient credit balance information.
 */
@Data
public class PatientCreditDTO {
    private Long id;
    private Long patientId;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}