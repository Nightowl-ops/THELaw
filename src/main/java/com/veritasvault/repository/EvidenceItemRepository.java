package com.veritasvault.repository;

import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, Long> {

    Optional<EvidenceItem> findByItemTrackingCode(String itemTrackingCode);

    boolean existsByItemTrackingCode(String itemTrackingCode);

    List<EvidenceItem> findByLegalCaseId(Long caseId);

    List<EvidenceItem> findByLegalCaseIdAndStatus(Long caseId, EvidenceStatus status);

    Page<EvidenceItem> findByLegalCaseId(Long caseId, Pageable pageable);

    @Query("SELECT e FROM EvidenceItem e WHERE " +
            "e.legalCase.id = :caseId AND " +
            "(:status IS NULL OR e.status = :status) AND " +
            "(:type IS NULL OR e.evidenceType = :type)")
    Page<EvidenceItem> findByCaseWithFilters(
            @Param("caseId") Long caseId,
            @Param("status") EvidenceStatus status,
            @Param("type") EvidenceType type,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EvidenceItem e WHERE e.id = :id")
    Optional<EvidenceItem> findByIdWithPessimisticLock(@Param("id") Long id);
}