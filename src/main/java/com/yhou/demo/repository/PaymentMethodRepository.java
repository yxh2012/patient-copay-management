package com.yhou.demo.repository;

import com.yhou.demo.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByPatientIdAndIsActiveTrue(Long patientId);

    Optional<PaymentMethod> findByIdAndPatientIdAndIsActiveTrue(Long id, Long patientId);

    List<PaymentMethod> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}