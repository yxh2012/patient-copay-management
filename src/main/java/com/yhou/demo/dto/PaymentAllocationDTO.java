package com.yhou.demo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing payment amount allocated to a specific copay.
 */
@Data
public class PaymentAllocationDTO {
    private Long id;
    private Long copayId;
    private BigDecimal amount;
}