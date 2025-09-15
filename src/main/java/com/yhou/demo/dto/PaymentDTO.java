package com.yhou.demo.dto;

import com.yhou.demo.entity.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing payment details with processor information and copay allocations.
 */
@Data
public class PaymentDTO {
    private Long id;
    private Long patientId;
    private Long paymentMethodId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String processorChargeId;
    private String failureCode;
    private LocalDateTime createdAt;
    private List<PaymentAllocationDTO> allocations;
}