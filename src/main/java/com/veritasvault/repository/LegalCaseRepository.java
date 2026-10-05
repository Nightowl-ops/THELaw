package com.veritasvault.repository;

import com.veritasvault.model.LegalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {
    Optional<LegalCase> findByCaseNumber(String caseNumber);
    boolean existsByCaseNumber(String caseNumber);
    List<LegalCase> findByLeadAttorneyId(Long attorneyId);
    List<LegalCase> findByClientId(Long clientId);
}