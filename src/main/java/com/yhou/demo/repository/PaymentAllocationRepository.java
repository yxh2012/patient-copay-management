package com.yhou.demo.repository;

import com.yhou.demo.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentId(Long paymentId);

    List<PaymentAllocation> findByCopayId(Long copayId);

    @Query("SELECT pa FROM PaymentAllocation pa WHERE pa.payment.id = :paymentId")
    List<PaymentAllocation> findByPaymentIdWithDetails(@Param("paymentId") Long paymentId);

    @Query("SELECT COALESCE(SUM(pa.amount), 0) FROM PaymentAllocation pa WHERE pa.copay.id = :copayId")
    BigDecimal getTotalAllocatedAmountForCopay(@Param("copayId") Long copayId);
}