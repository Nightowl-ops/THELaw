package com.veritasvault.repository;

import com.veritasvault.model.CustodyReservation;
import com.veritasvault.model.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository providing persistence operations and collision detection queries for CustodyReservation entities.
 */
@Repository
public interface CustodyReservationRepository extends JpaRepository<CustodyReservation, Long> {

    List<CustodyReservation> findByEvidenceItemId(Long evidenceId);

    Page<CustodyReservation> findByEvidenceItemId(Long evidenceId, Pageable pageable);

    List<CustodyReservation> findByExaminerId(Long examinerId);

    Page<CustodyReservation> findByExaminerId(Long examinerId, Pageable pageable);

    /**
     * Standard interval intersection collision query:
     * Overlap occurs if existing.startTime < requestedEnd AND existing.endTime > requestedStart.
     */
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