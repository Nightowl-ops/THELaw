package com.veritasvault.repository;

import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.enums.EvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, Long> {
    Optional<EvidenceItem> findByItemTrackingCode(String itemTrackingCode);
    boolean existsByItemTrackingCode(String itemTrackingCode);
    List<EvidenceItem> findByLegalCaseId(Long caseId);
    List<EvidenceItem> findByLegalCaseIdAndStatus(Long caseId, EvidenceStatus status);
}