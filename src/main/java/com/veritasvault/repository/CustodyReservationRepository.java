package com.veritasvault.repository;

import com.veritasvault.model.CustodyReservation;
import com.veritasvault.model.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustodyReservationRepository extends JpaRepository<CustodyReservation, Long> {

    List<CustodyReservation> findByEvidenceItemId(Long evidenceId);
    List<CustodyReservation> findByExaminerId(Long examinerId);

    // Double-booking check query
    @Query("SELECT r FROM CustodyReservation r " +
            "WHERE r.evidenceItem.id = :evidenceId " +
            "AND r.status IN (:activeStatuses) " +
            "AND (r.startTime < :requestedEnd AND r.endTime > :requestedStart)")
    List<CustodyReservation> findOverlappingReservations(
            @Param("evidenceId") Long evidenceId,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd,
            @Param("activeStatuses") List<ReservationStatus> activeStatuses
    );
}