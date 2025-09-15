package com.yhou.demo.repository;

import com.yhou.demo.entity.CreditTransaction;
import com.yhou.demo.entity.enums.CreditTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<CreditTransaction> findByPaymentId(Long paymentId);

    List<CreditTransaction> findByPatientIdAndTransactionType(Long patientId, CreditTransactionType transactionType);
}