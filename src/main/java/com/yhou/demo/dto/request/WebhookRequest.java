package com.yhou.demo.dto.request;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request object representing incoming webhook payload.
 */
@Data
public class WebhookRequest {

    @NotBlank(message = "{webhook.type.notBlank}")
    private String type;

    @NotBlank(message = "{webhook.processorChargeId.notBlank}")
    private String processorChargeId;

    @NotNull(message = "{webhook.amount.notNull}")
    @DecimalMin(value = "1.00", message = "{webhook.amount.min}")
    @Digits(integer = 8, fraction = 2, message = "{webhook.amount.digits}")
    private BigDecimal amount;

    private String failureCode;
}
