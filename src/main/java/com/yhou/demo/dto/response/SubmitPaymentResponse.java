package com.yhou.demo.dto.response;

import com.yhou.demo.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Value;

/**
 * RResponse DTO containing payment ID and processing status.
 */
@Value
@Builder
public class SubmitPaymentResponse {
    Long paymentId;
    PaymentStatus status;
}
