package com.yhou.demo.repository;

import com.yhou.demo.entity.Payment;
import com.yhou.demo.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRequestKey(String requestKey);

    Optional<Payment> findByProcessorChargeId(String processorChargeId);

    List<Payment> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    @Query("SELECT p FROM Payment p WHERE p.patient.id = :patientId AND p.status = :status")
    List<Payment> findByPatientIdAndStatus(@Param("patientId") Long patientId,
                                           @Param("status") PaymentStatus status);
}