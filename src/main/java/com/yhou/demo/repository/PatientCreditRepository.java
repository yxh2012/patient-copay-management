package com.yhou.demo.repository;

import com.yhou.demo.entity.PatientCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PatientCreditRepository extends JpaRepository<PatientCredit, Long> {

    Optional<PatientCredit> findByPatientId(Long patientId);

    @Modifying
    @Query("UPDATE PatientCredit pc SET pc.amount = pc.amount + :amount WHERE pc.patient.id = :patientId")
    int addCreditToPatient(@Param("patientId") Long patientId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE PatientCredit pc SET pc.amount = pc.amount - :amount WHERE pc.patient.id = :patientId AND pc.amount >= :amount")
    int deductCreditFromPatient(@Param("patientId") Long patientId, @Param("amount") BigDecimal amount);
}