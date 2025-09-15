package com.yhou.demo.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * DTO representing an individual copay allocation in a payment submission.
 */
@Data
public class PaymentAllocationRequest {

    @NotNull(message = "{paymentAllocationRequest.copayId.notNull}")
    private Long copayId;

    @NotNull(message = "{paymentAllocationRequest.amount.notNull}")
    @DecimalMin(value = "1.00", message = "{paymentAllocationRequest.amount.decimalMin}")
    @Digits(integer = 8, fraction = 2, message = "{paymentAllocationRequest.amount.digits}")
    private BigDecimal amount;
}
