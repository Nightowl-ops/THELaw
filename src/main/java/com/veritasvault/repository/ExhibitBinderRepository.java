package com.veritasvault.repository;

import com.veritasvault.model.ExhibitBinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for ExhibitBinder trial bundles.
 */
@Repository
public interface ExhibitBinderRepository extends JpaRepository<ExhibitBinder, Long> {

    List<ExhibitBinder> findByLegalCaseId(Long caseId);

    Page<ExhibitBinder> findByLegalCaseId(Long caseId, Pageable pageable);

    boolean existsByLegalCaseIdAndBinderName(Long caseId, String binderName);
}