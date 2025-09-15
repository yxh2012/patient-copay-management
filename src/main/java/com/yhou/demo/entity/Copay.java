package com.yhou.demo.entity;

import com.yhou.demo.entity.enums.CopayStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing patient copay obligations with payment tracking.
 */
@Entity
@Table(name = "copay")
@Data
@EqualsAndHashCode(exclude = {"visit", "paymentAllocations"})
@ToString(exclude = {"visit", "paymentAllocations"})
public class Copay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "remaining_balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopayStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "copay", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PaymentAllocation> paymentAllocations;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        // Set remaining balance to full amount initially
        if (remainingBalance == null) {
            remainingBalance = amount;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods for business logic
    public boolean isFullyPaid() {
        return remainingBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPartiallyPaid() {
        return remainingBalance.compareTo(BigDecimal.ZERO) > 0 &&
                remainingBalance.compareTo(amount) < 0;
    }

    public BigDecimal getPaidAmount() {
        return amount.subtract(remainingBalance);
    }
}