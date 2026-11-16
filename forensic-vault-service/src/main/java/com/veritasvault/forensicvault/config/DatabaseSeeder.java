package com.veritasvault.forensicvault.config;

import com.veritasvault.forensicvault.model.ChainOfCustodyLog;
import com.veritasvault.forensicvault.model.CustodyReservation;
import com.veritasvault.forensicvault.model.ForensicLabReport;
import com.veritasvault.forensicvault.model.enums.CustodyActionType;
import com.veritasvault.forensicvault.model.enums.LabIntegrityStatus;
import com.veritasvault.forensicvault.model.enums.ReservationStatus;
import com.veritasvault.forensicvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.forensicvault.repository.CustodyReservationRepository;
import com.veritasvault.forensicvault.repository.ForensicLabReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final CustodyReservationRepository reservationRepository;
    private final ForensicLabReportRepository labReportRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;

    @Override
    public void run(String... args) {
        if (custodyLogRepository.count() > 0 || reservationRepository.count() > 0) {
            log.info("Forensic Vault database already seeded with custody records. Skipping seeding.");
            return;
        }

        log.info("Seeding benchmark Forensic Lab & Custody records...");

        String sampleSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        // 1. Initial intake Chain of Custody ledger entry for benchmark Evidence #1
        ChainOfCustodyLog initialLog = ChainOfCustodyLog.builder()
                .evidenceId(1L)
                .actorUserId(2L) // Attorney
                .actionType(CustodyActionType.INGEST)
                .notes("Seed evidence intake. Baseline SHA-256 fingerprint verified against primary evidence locker.")
                .hashAtEvent(sampleSha256)
                .build();
        custodyLogRepository.save(initialLog);

        // 2. Completed Benchmark Forensic Custody Reservation
        CustodyReservation seedReservation = CustodyReservation.builder()
                .evidenceId(1L)
                .examinerId(3L) // Forensic Examiner
                .startTime(LocalDateTime.now().minusDays(1).withHour(9).withMinute(0))
                .endTime(LocalDateTime.now().minusDays(1).withHour(11).withMinute(0))
                .status(ReservationStatus.COMPLETED)
                .notes("Completed forensic extraction and cryptographic hash audit.")
                .build();
        CustodyReservation savedReservation = reservationRepository.save(seedReservation);

        // 3. Chain of Custody Check-Out entry
        ChainOfCustodyLog checkOutLog = ChainOfCustodyLog.builder()
                .evidenceId(1L)
                .actorUserId(3L)
                .actionType(CustodyActionType.CHECK_OUT)
                .notes("Evidence checked out for forensic lab extraction under reservation #" + savedReservation.getId())
                .hashAtEvent(sampleSha256)
                .build();
        custodyLogRepository.save(checkOutLog);

        // 4. Completed Forensic Lab Report
        ForensicLabReport seedReport = ForensicLabReport.builder()
                .custodyReservation(savedReservation)
                .evidenceItemId(1L)
                .examinerId(3L)
                .preExamHash(sampleSha256)
                .postExamHash(sampleSha256)
                .integrityStatus(LabIntegrityStatus.VERIFIED)
                .examinerFindings("Full physical Cellebrite UFED image extraction verified against original media. Zero bit-rot or byte variance detected.")
                .verifiedByThread("lab-verifier-pool-1")
                .verificationDurationMs(142L)
                .build();
        labReportRepository.save(seedReport);

        // 5. Chain of Custody Check-In entry
        ChainOfCustodyLog checkInLog = ChainOfCustodyLog.builder()
                .evidenceId(1L)
                .actorUserId(3L)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Evidence returned from forensic lab examination. Status: VERIFIED")
                .hashAtEvent(sampleSha256)
                .build();
        custodyLogRepository.save(checkInLog);

        log.info("Forensic Vault benchmark data seeded successfully: 1 completed reservation, 1 verified lab report, 3 custody log entries.");
    }
}
