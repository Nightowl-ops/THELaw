package com.veritasvault.caseevidence.repository;

import com.veritasvault.caseevidence.model.ExhibitBinder;
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
public interface ExhibitBinderRepository extends JpaRepository<ExhibitBinder, Long> {

    List<ExhibitBinder> findByLegalCaseId(Long caseId);

    Page<ExhibitBinder> findByLegalCaseId(Long caseId, Pageable pageable);

    boolean existsByLegalCaseIdAndBinderName(Long caseId, String binderName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ExhibitBinder b WHERE b.id = :id")
    Optional<ExhibitBinder> findByIdWithPessimisticLock(@Param("id") Long id);
}
