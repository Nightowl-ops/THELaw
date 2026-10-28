package com.veritasvault.service;

import com.veritasvault.dto.request.CreateLabReportRequest;
import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.dto.response.ForensicLabReportResponse;
import com.veritasvault.exception.ConflictException;
import com.veritasvault.model.CustodyReservation;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import com.veritasvault.model.enums.LabIntegrityStatus;
import com.veritasvault.model.enums.ReservationStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.repository.CustodyReservationRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.ForensicLabReportRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class CustodyReservationConcurrencyTest {

    @Autowired
    private CustodyReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LegalCaseRepository legalCaseRepository;

    @Autowired
    private EvidenceItemRepository evidenceItemRepository;

    @Autowired
    private CustodyReservationRepository reservationRepository;

    @Autowired
    private ForensicLabReportRepository labReportRepository;

    @Autowired
    private com.veritasvault.repository.ChainOfCustodyLogRepository custodyLogRepository;

    private User examiner;
    private LegalCase legalCase;
    private EvidenceItem sharedEvidence;

    @BeforeEach
    void setUp() {
        labReportRepository.deleteAll();
        custodyLogRepository.deleteAll();
        reservationRepository.deleteAll();
        evidenceItemRepository.deleteAll();
        legalCaseRepository.deleteAll();
        userRepository.deleteAll();

        examiner = userRepository.save(User.builder()
                .fullName("Agent Dana Scully")
                .email("scully.concurrency@veritasvault.com")
                .passwordHash("DummyPasswordHash123$")
                .role(Role.ROLE_FORENSIC_EXAMINER)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build());

        legalCase = legalCaseRepository.save(LegalCase.builder()
                .caseNumber("VV-2026-TEST")
                .caseTitle("State v. Concurrent Access")
                .caseType(CaseType.DEFENSE)
                .status(CaseStatus.ACTIVE)
                .leadAttorney(examiner)
                .build());

        byte[] sampleBytes = "Real forensic raw byte payload for lab report test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String realSha256;
        try {
            java.nio.file.Path testPath = java.nio.file.Paths.get("uploads", "evidence", "test", "sample.dat");
            java.nio.file.Files.createDirectories(testPath.getParent());
            java.nio.file.Files.write(testPath, sampleBytes);
            realSha256 = com.veritasvault.util.ChecksumUtils.calculateSha256(new java.io.ByteArrayInputStream(sampleBytes));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to seed test evidence file", e);
        }

        sharedEvidence = evidenceItemRepository.save(EvidenceItem.builder()
                .legalCase(legalCase)
                .name("Encrypted iPhone 15 Pro")
                .itemTrackingCode("EV-CONCURRENCY-001")
                .evidenceType(EvidenceType.DEVICE_IMAGE)
                .sourceOrigin("Officer Locker #12")
                .storageUrl("evidence/test/sample.dat")
                .fileSizeBytes((long) sampleBytes.length)
                .sha256Hash(realSha256)
                .status(EvidenceStatus.AVAILABLE)
                .uploadedBy(examiner)
                .build());
    }

    @Test
    @DisplayName("Concurrency Test: 5 concurrent threads attempting to book the same evidence item for overlapping intervals - Exactly 1 must win (201) and 4 must receive 409 Conflict")
    void concurrentBooking_ShouldAllowOnlyOneWinnerAndRejectCompetitors() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger conflictCounter = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        LocalDateTime targetStart = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0);
        LocalDateTime targetEnd = targetStart.plusHours(2);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i + 1;
            executor.submit(() -> {
                String threadName = "Examiner-Worker-" + threadIndex;
                Thread.currentThread().setName(threadName);

                readyLatch.countDown();
                try {
                    // Wait for all threads to align at the starting gate
                    startLatch.await();

                    log.info("[{}] Firing booking request at database for evidence ID: {}",
                            threadName, sharedEvidence.getId());

                    CustodyReservationRequest request = CustodyReservationRequest.builder()
                            .evidenceItemId(sharedEvidence.getId())
                            .startTime(targetStart)
                            .endTime(targetEnd)
                            .notes("Concurrent booking from " + threadName)
                            .build();

                    CustodyReservationResponse response = reservationService.createReservation(request, examiner.getId());
                    if (response != null && response.getStatus() == ReservationStatus.CONFIRMED) {
                        successCounter.incrementAndGet();
                        log.info(">>> [{}] SUCCESS! Acquired lock and booked reservation ID: {}",
                                threadName, response.getId());
                    }
                } catch (ConflictException ex) {
                    conflictCounter.incrementAndGet();
                    log.info("<<< [{}] EXPECTED 409 CONFLICT: {}", threadName, ex.getMessage());
                } catch (Throwable t) {
                    synchronized (unexpectedErrors) {
                        unexpectedErrors.add(t);
                    }
                    log.error("[{}] Unexpected error: {}", threadName, t.getMessage());
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        // Release all threads simultaneously
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(unexpectedErrors).isEmpty();

        log.info("CONCURRENCY TEST SUMMARY -> Total: {}, Success: {}, 409 Conflicts: {}",
                threadCount, successCounter.get(), conflictCounter.get());

        // Concurrency assertion: exactly 1 winner and 4 rejected
        assertThat(successCounter.get()).isEqualTo(1);
        assertThat(conflictCounter.get()).isEqualTo(threadCount - 1);

        // Database assertion: exactly 1 reservation row exists
        List<CustodyReservation> inDb = reservationRepository.findByEvidenceItemId(sharedEvidence.getId());
        assertThat(inDb).hasSize(1);
        assertThat(inDb.get(0).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Multithreading Test: Parallel hash verification using ExecutorService pool records thread name and telemetry")
    void checkInWithLabReport_ExecutesOnDedicatedPoolThread() {
        // Create an active reservation
        CustodyReservation reservation = reservationRepository.save(CustodyReservation.builder()
                .evidenceItem(sharedEvidence)
                .examiner(examiner)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(3))
                .status(ReservationStatus.ACTIVE)
                .notes("Active forensic lab analysis")
                .build());

        sharedEvidence.setStatus(EvidenceStatus.IN_LAB);
        evidenceItemRepository.save(sharedEvidence);

        CreateLabReportRequest request = CreateLabReportRequest.builder()
                .examinerFindings("Hardware extraction completed cleanly; all flash sectors intact")
                .notes("Returned to vault shelf 4B")
                .build();

        ForensicLabReportResponse reportResponse = reservationService.checkInWithLabReport(
                reservation.getId(), request, examiner.getId()
        );

        assertThat(reportResponse).isNotNull();
        assertThat(reportResponse.getIntegrityStatus()).isEqualTo(LabIntegrityStatus.VERIFIED);
        assertThat(reportResponse.getPreExamHash()).isEqualTo(sharedEvidence.getSha256Hash());
        assertThat(reportResponse.getPostExamHash()).isEqualTo(sharedEvidence.getSha256Hash());

        // Assert that execution ran on our dedicated ExecutorService thread pool
        assertThat(reportResponse.getVerifiedByThread()).startsWith("lab-verifier-pool-");
        assertThat(reportResponse.getVerificationDurationMs()).isGreaterThanOrEqualTo(0L);

        log.info("MULTITHREADING REPORT TELEMETRY: Thread={}, Duration={} ms, Status={}",
                reportResponse.getVerifiedByThread(),
                reportResponse.getVerificationDurationMs(),
                reportResponse.getIntegrityStatus());
    }
}
