package com.veritasvault.repository;

import com.veritasvault.model.LegalCase;
import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for LegalCase master records.
 */
@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

    Optional<LegalCase> findByCaseNumber(String caseNumber);

    boolean existsByCaseNumber(String caseNumber);

    List<LegalCase> findByLeadAttorneyId(Long attorneyId);

    List<LegalCase> findByClientId(Long clientId);

    Page<LegalCase> findByClientId(Long clientId, Pageable pageable);

    @Query("SELECT c FROM LegalCase c WHERE " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:type IS NULL OR c.caseType = :type) AND " +
            "(:search IS NULL OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.caseTitle) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LegalCase> searchCases(
            @Param("status") CaseStatus status,
            @Param("type") CaseType type,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT c FROM LegalCase c WHERE " +
            "c.client.id = :clientId AND " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:type IS NULL OR c.caseType = :type) AND " +
            "(:search IS NULL OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.caseTitle) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LegalCase> searchCasesByClient(
            @Param("clientId") Long clientId,
            @Param("status") CaseStatus status,
            @Param("type") CaseType type,
            @Param("search") String search,
            Pageable pageable
    );
}