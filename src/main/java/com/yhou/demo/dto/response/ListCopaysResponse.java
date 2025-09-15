package com.yhou.demo.dto.response;

import com.yhou.demo.dto.CopayDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO containing patient copays with financial summaries and payment statistics.
 */
@Data
public class ListCopaysResponse {
    private List<CopayDTO> copays;
    private BigDecimal totalAmount;
    private BigDecimal totalRemainingBalance; // total still owed
    private BigDecimal totalPaidAmount;       // total already paid
    private Integer count;                    // total size of copay
    private PaymentSummary summary;           // payment summary

    @Data
    public static class PaymentSummary {
        private long fullyPaidCount;
        private long partiallyPaidCount;
        private long unpaidCount;
        private long writeOffCount;
    }

    public ListCopaysResponse(List<CopayDTO> copays) {
        this.copays = copays;
        this.count = copays.size();

        this.totalAmount = copays.stream()
                .map(CopayDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalRemainingBalance = copays.stream()
                .map(CopayDTO::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalPaidAmount = totalAmount.subtract(totalRemainingBalance);

        this.summary = new PaymentSummary();
        this.summary.fullyPaidCount = copays.stream().filter(CopayDTO::isFullyPaid).count();
        this.summary.partiallyPaidCount = copays.stream().filter(CopayDTO::isPartiallyPaid).count();
        this.summary.unpaidCount = copays.stream()
                .filter(c -> c.getRemainingBalance().equals(c.getAmount()))
                .count();
        this.summary.writeOffCount = copays.stream()
                .filter(c -> "WRITE_OFF".equals(c.getStatus().name()))
                .count();
    }
}