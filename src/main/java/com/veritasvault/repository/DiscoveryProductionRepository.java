package com.veritasvault.repository;

import com.veritasvault.model.DiscoveryProduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscoveryProductionRepository extends JpaRepository<DiscoveryProduction, Long> {
    Optional<DiscoveryProduction> findByAccessToken(String accessToken);
    List<DiscoveryProduction> findByLegalCaseId(Long caseId);
}