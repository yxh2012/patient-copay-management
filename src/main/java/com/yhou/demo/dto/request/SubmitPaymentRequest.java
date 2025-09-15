package com.yhou.demo.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import static com.yhou.demo.constants.ApplicationConstants.DEFAULT_CURRENCY;

/**
 * Request DTO for submitting patient payments with copay allocations.
 */
@Data
public class SubmitPaymentRequest {
    @NotNull(message = "{submitPaymentRequest.paymentMethodId.notNull}")
    private Long paymentMethodId;

    @NotNull(message = "{submitPaymentRequest.currency.notNull}")
    @Pattern(regexp = "USD", message = "{submitPaymentRequest.currency.pattern}")
    private String currency = DEFAULT_CURRENCY;

    @NotEmpty(message = "{submitPaymentRequest.allocations.notEmpty}")
    @Valid
    private List<PaymentAllocationRequest> allocations;
}

