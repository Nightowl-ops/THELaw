package com.veritasvault.repository;

import com.veritasvault.model.ChainOfCustodyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChainOfCustodyLogRepository extends JpaRepository<ChainOfCustodyLog, Long> {
    List<ChainOfCustodyLog> findByEvidenceItemIdOrderByLoggedAtAsc(Long evidenceId);
}