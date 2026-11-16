package com.veritasvault.forensicvault.repository;

import com.veritasvault.forensicvault.model.CustodyReservation;
import com.veritasvault.forensicvault.model.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustodyReservationRepository extends JpaRepository<CustodyReservation, Long> {

    List<CustodyReservation> findByEvidenceId(Long evidenceId);

    Page<CustodyReservation> findByEvidenceId(Long evidenceId, Pageable pageable);

    List<CustodyReservation> findByExaminerId(Long examinerId);

    Page<CustodyReservation> findByExaminerId(Long examinerId, Pageable pageable);

    List<CustodyReservation> findByExaminerIdAndStatusIn(Long examinerId, List<ReservationStatus> statuses);

    @Query("SELECT r FROM CustodyReservation r " +
            "WHERE r.evidenceId = :evidenceId " +
            "AND r.status IN (:activeStatuses) " +
            "AND (r.startTime < :requestedEnd AND r.endTime > :requestedStart)")
    List<CustodyReservation> findOverlappingReservations(
            @Param("evidenceId") Long evidenceId,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd,
            @Param("activeStatuses") List<ReservationStatus> activeStatuses
    );
}
