package com.yhou.demo.dto;

import com.yhou.demo.entity.enums.CreditTransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing patient credit transactions and payment history records.
 */
@Data
public class CreditTransactionDTO {
    private Long id;
    private Long patientId;
    private Long paymentId;
    private BigDecimal amount;
    private CreditTransactionType transactionType;
    private String description;
    private LocalDateTime createdAt;
}