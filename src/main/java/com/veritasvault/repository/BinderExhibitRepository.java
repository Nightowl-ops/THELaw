package com.veritasvault.repository;

import com.veritasvault.model.BinderExhibit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BinderExhibitRepository extends JpaRepository<BinderExhibit, Long> {
    List<BinderExhibit> findByBinderIdOrderByBatesStartNumAsc(Long binderId);
    Optional<BinderExhibit> findByBinderIdAndEvidenceItemId(Long binderId, Long evidenceId);
    boolean existsByBinderIdAndEvidenceItemId(Long binderId, Long evidenceId);
}