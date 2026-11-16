package com.veritasvault.forensicvault.repository;

import com.veritasvault.forensicvault.model.ChainOfCustodyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChainOfCustodyLogRepository extends JpaRepository<ChainOfCustodyLog, Long> {
    List<ChainOfCustodyLog> findByEvidenceIdOrderByLoggedAtAsc(Long evidenceId);
}
