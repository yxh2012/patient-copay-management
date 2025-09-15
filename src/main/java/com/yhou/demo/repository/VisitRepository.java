package com.yhou.demo.repository;

import com.yhou.demo.entity.Visit;
import com.yhou.demo.entity.enums.VisitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findByPatientIdOrderByVisitDateDesc(Long patientId);

    List<Visit> findByPatientIdAndVisitDateBetween(Long patientId, LocalDate startDate, LocalDate endDate);

    List<Visit> findByPatientIdAndVisitType(Long patientId, VisitType visitType);

    @Query("SELECT v FROM Visit v WHERE v.patient.id = :patientId AND v.doctorName = :doctorName ORDER BY v.visitDate DESC")
    List<Visit> findByPatientIdAndDoctorName(@Param("patientId") Long patientId, @Param("doctorName") String doctorName);
}