package com.veritasvault.repository;

import com.veritasvault.model.ExhibitBinder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExhibitBinderRepository extends JpaRepository<ExhibitBinder, Long> {
    List<ExhibitBinder> findByLegalCaseId(Long caseId);
}