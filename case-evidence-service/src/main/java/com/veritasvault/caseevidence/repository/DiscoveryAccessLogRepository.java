package com.veritasvault.caseevidence.repository;

import com.veritasvault.caseevidence.model.DiscoveryAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscoveryAccessLogRepository extends JpaRepository<DiscoveryAccessLog, Long> {
    List<DiscoveryAccessLog> findByProductionIdOrderByAccessedAtDesc(Long productionId);
}
