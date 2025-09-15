package com.yhou.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO containing AI-generated copay analysis and recommendations.
 */
@Value
@Builder
public class CopayAISummaryResponse {
    Long patientId;
    String patientName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDateTime generatedAt;

    String accountStatus;
    FinancialOverview financialOverview;
    List<String> recommendations;
    List<String> insights;
    String summarySource; // "AI" or "SYSTEM"

    @Value
    @Builder
    public static class FinancialOverview {
        String outstandingBalance;
        String totalAmount;
        int totalCopays;
        int paidCopays;
        int unpaidCopays;
        int partiallyPaidCopays;
    }
}