package com.yhou.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing allocation of payment amounts to specific copays.
 */
@Entity
@Table(name = "payment_allocation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "copay_id"}))
@Data
@EqualsAndHashCode(exclude = {"payment", "copay"})
@ToString(exclude = {"payment", "copay"})
public class PaymentAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copay_id", nullable = false)
    private Copay copay;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
