package com.veritasvault.repository;

import com.veritasvault.model.ForensicLabReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ForensicLabReportRepository extends JpaRepository<ForensicLabReport, Long> {

    Optional<ForensicLabReport> findByCustodyReservationId(Long reservationId);

    List<ForensicLabReport> findByEvidenceItemId(Long evidenceItemId);

    List<ForensicLabReport> findByExaminerId(Long examinerId);
}
