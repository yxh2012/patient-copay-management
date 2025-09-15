package com.yhou.demo.repository;

import com.yhou.demo.entity.Copay;
import com.yhou.demo.entity.enums.CopayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CopayRepository extends JpaRepository<Copay, Long> {

    @Query("""
       SELECT c FROM Copay c JOIN c.visit v
       WHERE v.patient.id = :patientId
         AND (:status IS NULL OR c.status = :status)
       ORDER BY c.createdAt DESC
       """)
    List<Copay> findCopaysByPatientIdAndOptionalStatus(@Param("patientId") Long patientId,
                                                       @Param("status") CopayStatus status);

    @Query("SELECT c FROM Copay c JOIN c.visit v WHERE v.patient.id = :patientId AND c.status = :status ORDER BY c.createdAt DESC")
    List<Copay> findByPatientIdAndStatus(@Param("patientId") Long patientId, @Param("status") CopayStatus status);

    @Query("SELECT c FROM Copay c JOIN c.visit v WHERE v.patient.id = :patientId ORDER BY c.createdAt DESC")
    List<Copay> findByPatientIdOrderByCreatedAtDesc(@Param("patientId") Long patientId);

    // Updated to support multiple statuses (PAYABLE and PARTIALLY_PAID)
    @Query("SELECT c FROM Copay c JOIN c.visit v WHERE c.id IN :copayIds AND v.patient.id = :patientId AND c.status IN :statuses")
    List<Copay> findCopaysByIdsAndPatientIdAndStatusIn(@Param("copayIds") List<Long> copayIds,
                                                       @Param("patientId") Long patientId,
                                                       @Param("statuses") List<CopayStatus> statuses);

    @Modifying
    @Query("UPDATE Copay c SET c.status = :status WHERE c.id IN :copayIds")
    int updateCopayStatus(@Param("copayIds") List<Long> copayIds, @Param("status") CopayStatus status);

    List<Copay> findByVisitId(Long visitId);
}