package com.veritasvault.forensicvault.service;

import com.veritasvault.forensicvault.client.AuthServiceClient;
import com.veritasvault.forensicvault.client.CaseServiceClient;
import com.veritasvault.forensicvault.client.EvidenceItemDto;
import com.veritasvault.forensicvault.client.LegalCaseDto;
import com.veritasvault.forensicvault.client.UserSummaryDto;
import com.veritasvault.forensicvault.dto.request.CreateLabReportRequest;
import com.veritasvault.forensicvault.dto.request.CustodyReservationRequest;
import com.veritasvault.forensicvault.dto.response.CaseAuditResponse;
import com.veritasvault.forensicvault.dto.response.CustodyReservationResponse;
import com.veritasvault.forensicvault.dto.response.ForensicLabReportResponse;
import com.veritasvault.forensicvault.exception.BadRequestException;
import com.veritasvault.forensicvault.exception.ConflictException;
import com.veritasvault.forensicvault.exception.ResourceNotFoundException;
import com.veritasvault.forensicvault.kafka.VaultKafkaProducer;
import com.veritasvault.forensicvault.model.ChainOfCustodyLog;
import com.veritasvault.forensicvault.model.CustodyReservation;
import com.veritasvault.forensicvault.model.ForensicLabReport;
import com.veritasvault.forensicvault.model.enums.CustodyActionType;
import com.veritasvault.forensicvault.model.enums.EvidenceStatus;
import com.veritasvault.forensicvault.model.enums.LabIntegrityStatus;
import com.veritasvault.forensicvault.model.enums.ReservationStatus;
import com.veritasvault.forensicvault.model.enums.Role;
import com.veritasvault.forensicvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.forensicvault.repository.CustodyReservationRepository;
import com.veritasvault.forensicvault.repository.ForensicLabReportRepository;
import com.veritasvault.forensicvault.util.ChecksumUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustodyReservationService {

    private final CustodyReservationRepository reservationRepository;
    private final ForensicLabReportRepository labReportRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuthServiceClient authServiceClient;
    private final CaseServiceClient caseServiceClient;
    private final VaultKafkaProducer vaultKafkaProducer;

    // Multithreading thread pool for parallel forensic cryptographic hash verifications
    private final ExecutorService labVerificationExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "lab-verifier-pool-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down lab verification ExecutorService pool...");
        labVerificationExecutor.shutdown();
    }

    @Transactional
    public CustodyReservationResponse createReservation(CustodyReservationRequest request, Long examinerId, String jwt) {
        log.info("[Thread: {}] Attempting to book custody reservation for evidence item ID: {}",
                Thread.currentThread().getName(), request.getEvidenceItemId());

        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BadRequestException("Reservation start time must be strictly before end time");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cannot create a reservation for a date or time in the past");
        }

        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(request.getEvidenceItemId(), jwt);
        if (evidence == null) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + request.getEvidenceItemId());
        }

        if (evidence.getStatus() == EvidenceStatus.SEQUESTERED || evidence.getStatus() == EvidenceStatus.ADMITTED) {
            throw new BadRequestException("Evidence item cannot be reserved: current status is " + evidence.getStatus());
        }

        UserSummaryDto examiner = authServiceClient.getUserById(examinerId);
        if (examiner == null) {
            throw new ResourceNotFoundException("User not found with ID: " + examinerId);
        }

        if (!"ROLE_FORENSIC_EXAMINER".equals(examiner.getRole()) && !"ROLE_ADMIN".equals(examiner.getRole())) {
            throw new BadRequestException("Only users with ROLE_FORENSIC_EXAMINER or ROLE_ADMIN can reserve custody slots");
        }

        List<ReservationStatus> blockingStatuses = List.of(ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE);
        List<CustodyReservation> overlaps = reservationRepository.findOverlappingReservations(
                evidence.getId(),
                request.getStartTime(),
                request.getEndTime(),
                blockingStatuses
        );

        if (!overlaps.isEmpty()) {
            log.warn("[Thread: {}] Concurrency collision: Evidence item ID {} already reserved during requested interval",
                    Thread.currentThread().getName(), evidence.getId());
            throw new ConflictException("Time conflict: Evidence item is already booked during the requested interval");
        }

        CustodyReservation reservation = CustodyReservation.builder()
                .evidenceId(evidence.getId())
                .examinerId(examiner.getId())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .notes(request.getNotes())
                .build();

        CustodyReservation savedReservation = reservationRepository.save(reservation);

        caseServiceClient.updateEvidenceStatus(evidence.getId(), EvidenceStatus.RESERVED,
                "Booked for custody reservation #" + savedReservation.getId(), jwt);

        CustodyReservationResponse response = mapToResponse(savedReservation, evidence, examiner);
        notificationService.broadcast("CUSTODY_RESERVATION_CREATED", response);

        log.info("[Thread: {}] Successfully created reservation ID: {} for evidence ID: {}",
                Thread.currentThread().getName(), savedReservation.getId(), evidence.getId());
        return response;
    }

    @Transactional
    public CustodyReservationResponse cancelReservation(Long reservationId, Long currentUserId, String userRole, String jwt) {
        CustodyReservation reservation = findReservationById(reservationId);

        boolean isAssignedExaminer = reservation.getExaminerId().equals(currentUserId);
        boolean isAdmin = "ROLE_ADMIN".equals(userRole);

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You do not have permission to cancel this reservation");
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed reservation");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Reservation is already cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        CustodyReservation updated = reservationRepository.save(reservation);

        caseServiceClient.updateEvidenceStatus(reservation.getEvidenceId(), EvidenceStatus.AVAILABLE,
                "Reservation #" + reservation.getId() + " cancelled. Evidence slot released.", jwt);

        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(reservation.getEvidenceId(), jwt);
        UserSummaryDto examiner = authServiceClient.getUserById(reservation.getExaminerId());

        ChainOfCustodyLog cancellationLog = ChainOfCustodyLog.builder()
                .evidenceId(reservation.getEvidenceId())
                .actorUserId(currentUserId)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Reservation #" + reservation.getId() + " cancelled. Evidence slot released.")
                .hashAtEvent(evidence != null && evidence.getSha256Checksum() != null ? evidence.getSha256Checksum() : "N/A")
                .build();
        custodyLogRepository.save(cancellationLog);

        CustodyReservationResponse response = mapToResponse(updated, evidence, examiner);
        notificationService.broadcast("CUSTODY_RESERVATION_CANCELLED", response);
        return response;
    }

    @Transactional
    public CustodyReservationResponse checkOutEvidence(Long reservationId, Long currentUserId, String userRole, String jwt) {
        CustodyReservation reservation = findReservationById(reservationId);

        boolean isAssignedExaminer = reservation.getExaminerId().equals(currentUserId);
        boolean isAdmin = "ROLE_ADMIN".equals(userRole);

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check out reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Only CONFIRMED reservations can be checked out");
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        CustodyReservation updated = reservationRepository.save(reservation);

        caseServiceClient.updateEvidenceStatus(reservation.getEvidenceId(), EvidenceStatus.IN_LAB,
                "Evidence checked out for lab examination under reservation #" + reservation.getId(), jwt);

        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(reservation.getEvidenceId(), jwt);
        UserSummaryDto examiner = authServiceClient.getUserById(reservation.getExaminerId());

        ChainOfCustodyLog logEntry = ChainOfCustodyLog.builder()
                .evidenceId(reservation.getEvidenceId())
                .actorUserId(currentUserId)
                .actionType(CustodyActionType.CHECK_OUT)
                .notes("Evidence checked out for lab examination under reservation #" + reservation.getId())
                .hashAtEvent(evidence != null && evidence.getSha256Checksum() != null ? evidence.getSha256Checksum() : "N/A")
                .build();
        custodyLogRepository.save(logEntry);

        CustodyReservationResponse response = mapToResponse(updated, evidence, examiner);
        notificationService.broadcast("EVIDENCE_CHECKED_OUT", response);
        return response;
    }

    /**
     * Multithreaded Check-In with parallel cryptographic SHA-256 hash verification.
     * Uses ExecutorService pool to execute the hash computation as an asynchronous Callable task,
     * comparing the pre-examination hash against the post-examination file state.
     */
    @Transactional
    public ForensicLabReportResponse checkInWithLabReport(Long reservationId, CreateLabReportRequest request, Long currentUserId, String userRole, String jwt) {
        log.info("[Main-Thread: {}] Initiating forensic check-in for reservation ID: {}",
                Thread.currentThread().getName(), reservationId);

        CustodyReservation reservation = findReservationById(reservationId);

        boolean isAssignedExaminer = reservation.getExaminerId().equals(currentUserId);
        boolean isAdmin = "ROLE_ADMIN".equals(userRole);

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check in reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE reservations can be checked in");
        }

        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(reservation.getEvidenceId(), jwt);
        if (evidence == null) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + reservation.getEvidenceId());
        }

        String preExamHash = evidence.getSha256Checksum();

        // Submit Callable task to ExecutorService thread pool
        long startTime = System.currentTimeMillis();
        Future<VerificationResult> future = labVerificationExecutor.submit(() -> {
            String workerThread = Thread.currentThread().getName();
            log.info("[Worker-Thread: {}] Executing parallel cryptographic verification on evidence: {} ({})",
                    workerThread, evidence.getName(), evidence.getTrackingCode());

            String computedHash;
            try {
                if (evidence.getFileStoragePath() != null && !evidence.getFileStoragePath().isBlank() && fileStorageService != null) {
                    Resource resource = fileStorageService.loadAsResource(evidence.getFileStoragePath());
                    if (resource != null && resource.exists() && resource.isReadable()) {
                        try (InputStream is = resource.getInputStream()) {
                            computedHash = ChecksumUtils.calculateSha256(is);
                        }
                    } else {
                        log.warn("[Worker-Thread: {}] Evidence file does not exist or is not readable: {}",
                                workerThread, evidence.getFileStoragePath());
                        computedHash = "FILE_NOT_FOUND_OR_UNREADABLE";
                    }
                } else {
                    log.warn("[Worker-Thread: {}] No storage path associated with evidence #{}",
                            workerThread, evidence.getId());
                    computedHash = "STORAGE_URL_MISSING";
                }
            } catch (Exception ex) {
                log.warn("[Worker-Thread: {}] Could not read file stream from storage: {}. Evidence integrity compromised.",
                        workerThread, ex.getMessage());
                computedHash = "FILE_READ_ERROR: " + ex.getMessage();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Worker-Thread: {}] Finished verification in {} ms. Computed hash: {}",
                    workerThread, duration, computedHash);

            return new VerificationResult(computedHash, workerThread, duration);
        });

        VerificationResult result;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verification interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Verification execution failed: " + e.getCause().getMessage(), e);
        }

        String postExamHash = result.computedHash();
        LabIntegrityStatus integrityStatus;

        if (preExamHash != null && preExamHash.equalsIgnoreCase(postExamHash)) {
            integrityStatus = LabIntegrityStatus.VERIFIED;
            caseServiceClient.updateEvidenceStatus(evidence.getId(), EvidenceStatus.AVAILABLE,
                    "Evidence returned and verified with SHA-256 integrity intact.", jwt);
        } else {
            integrityStatus = LabIntegrityStatus.COMPROMISED;
            caseServiceClient.updateEvidenceStatus(evidence.getId(), EvidenceStatus.SEQUESTERED,
                    "FORENSIC ALERT: Hash mismatch on check-in! Evidence quarantined.", jwt);

            // Publish Kafka tamper event to trigger automated distributed quarantine
            vaultKafkaProducer.publishEvidenceTampered(
                    evidence.getId(),
                    preExamHash,
                    postExamHash,
                    currentUserId,
                    "Forensic SHA-256 mismatch detected during lab check-in"
            );

            log.warn("FORENSIC ALERT: Hash mismatch on check-in for evidence #{}! Expected: {}, Computed: {}",
                    evidence.getId(), preExamHash, postExamHash);
        }

        reservation.setStatus(ReservationStatus.COMPLETED);
        CustodyReservation updatedReservation = reservationRepository.save(reservation);

        ForensicLabReport labReport = ForensicLabReport.builder()
                .custodyReservation(updatedReservation)
                .evidenceItemId(evidence.getId())
                .examinerId(currentUserId)
                .preExamHash(preExamHash != null ? preExamHash : "UNKNOWN")
                .postExamHash(postExamHash)
                .integrityStatus(integrityStatus)
                .examinerFindings(request != null ? request.getExaminerFindings() : null)
                .verifiedByThread(result.threadName())
                .verificationDurationMs(result.durationMs())
                .build();

        ForensicLabReport savedReport = (labReportRepository != null) ? labReportRepository.save(labReport) : labReport;

        String checkInNotes = (request != null && request.getNotes() != null && !request.getNotes().isBlank())
                ? request.getNotes()
                : "Evidence returned from forensic lab examination. Status: " + integrityStatus;

        ChainOfCustodyLog logEntry = ChainOfCustodyLog.builder()
                .evidenceId(evidence.getId())
                .actorUserId(currentUserId)
                .actionType(CustodyActionType.CHECK_IN)
                .notes(checkInNotes)
                .hashAtEvent(postExamHash)
                .build();
        custodyLogRepository.save(logEntry);

        UserSummaryDto examiner = authServiceClient.getUserById(currentUserId);
        ForensicLabReportResponse response = mapToReportResponse(savedReport, evidence, examiner);
        notificationService.broadcast(
                integrityStatus == LabIntegrityStatus.COMPROMISED ? "EVIDENCE_COMPROMISED" : "EVIDENCE_CHECKED_IN",
                response
        );

        return response;
    }

    @Transactional
    public CustodyReservationResponse checkInEvidence(Long reservationId, String checkInNotes, Long currentUserId, String userRole, String jwt) {
        return checkInEvidence(reservationId, checkInNotes, CustodyActionType.CHECK_IN, currentUserId, userRole, jwt);
    }

    @Transactional
    public CustodyReservationResponse checkInEvidence(Long reservationId, String checkInNotes, CustodyActionType actionType, Long currentUserId, String userRole, String jwt) {
        CreateLabReportRequest reportRequest = CreateLabReportRequest.builder()
                .notes(checkInNotes)
                .examinerFindings(checkInNotes)
                .build();
        checkInWithLabReport(reservationId, reportRequest, currentUserId, userRole, jwt);
        CustodyReservation reservation = findReservationById(reservationId);
        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(reservation.getEvidenceId(), jwt);
        UserSummaryDto examiner = authServiceClient.getUserById(reservation.getExaminerId());
        return mapToResponse(reservation, evidence, examiner);
    }

    @Transactional(readOnly = true)
    public ForensicLabReportResponse getLabReportByReservationId(Long reservationId, String jwt) {
        ForensicLabReport report = labReportRepository.findByCustodyReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Forensic lab report not found for reservation ID: " + reservationId));
        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(report.getEvidenceItemId(), jwt);
        UserSummaryDto examiner = authServiceClient.getUserById(report.getExaminerId());
        return mapToReportResponse(report, evidence, examiner);
    }

    /**
     * Batch Case Integrity Audit executing parallel cryptographic SHA-256 verifications
     * across all evidence items in a case concurrently using invokeAll() on the 4-worker thread pool.
     */
    @Transactional
    public CaseAuditResponse auditCaseEvidenceIntegrity(Long caseId, Long currentUserId, String userRole, String jwt) {
        LegalCaseDto legalCase = caseServiceClient.getCaseById(caseId, jwt);
        if (legalCase == null) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }

        boolean isAuthorized = "ROLE_ATTORNEY".equals(userRole) || "ROLE_FORENSIC_EXAMINER".equals(userRole) || "ROLE_ADMIN".equals(userRole);
        if (!isAuthorized) {
            throw new AccessDeniedException("Only attorneys, forensic examiners, or administrators can run case audits");
        }

        List<EvidenceItemDto> items = caseServiceClient.getEvidenceByCaseId(caseId, jwt);
        if (items.isEmpty()) {
            return CaseAuditResponse.builder()
                    .caseId(caseId)
                    .caseNumber(legalCase.getCaseNumber())
                    .caseTitle(legalCase.getCaseTitle())
                    .totalEvidenceAudited(0)
                    .verifiedCount(0)
                    .compromisedCount(0)
                    .totalDurationMs(0)
                    .auditItems(List.of())
                    .auditedAt(LocalDateTime.now())
                    .build();
        }

        log.info("[Main-Thread: {}] Initiating parallel case integrity audit for case #{} ({} evidence items)",
                Thread.currentThread().getName(), legalCase.getCaseNumber(), items.size());

        long batchStartTime = System.currentTimeMillis();

        // Prepare parallel Callable tasks for all evidence items
        List<Callable<CaseAuditResponse.EvidenceAuditItem>> tasks = new ArrayList<>();
        for (EvidenceItemDto item : items) {
            tasks.add(() -> {
                long itemStart = System.currentTimeMillis();
                String workerThread = Thread.currentThread().getName();
                String computedHash = null;
                LabIntegrityStatus status;

                try {
                    if (item.getFileStoragePath() != null && !item.getFileStoragePath().isBlank() && fileStorageService != null) {
                        Resource resource = fileStorageService.loadAsResource(item.getFileStoragePath());
                        if (resource != null && resource.exists() && resource.isReadable()) {
                            try (InputStream is = resource.getInputStream()) {
                                computedHash = ChecksumUtils.calculateSha256(is);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[Worker-Thread: {}] Audit could not read evidence #{} ({}): {}",
                            workerThread, item.getId(), item.getTrackingCode(), ex.getMessage());
                }

                if (computedHash != null && item.getSha256Checksum() != null && item.getSha256Checksum().equalsIgnoreCase(computedHash)) {
                    status = LabIntegrityStatus.VERIFIED;
                } else {
                    status = LabIntegrityStatus.COMPROMISED;
                }

                long itemDuration = System.currentTimeMillis() - itemStart;
                log.info("[Worker-Thread: {}] Audited evidence #{} ({}) in {} ms. Status: {}",
                        workerThread, item.getId(), item.getTrackingCode(), itemDuration, status);

                return CaseAuditResponse.EvidenceAuditItem.builder()
                        .evidenceId(item.getId())
                        .trackingCode(item.getTrackingCode())
                        .name(item.getName())
                        .baselineHash(item.getSha256Checksum())
                        .computedHash(computedHash != null ? computedHash : "UNREADABLE_OR_MISSING")
                        .status(status)
                        .verifiedByThread(workerThread)
                        .durationMs(itemDuration)
                        .build();
            });
        }

        // Execute all tasks concurrently using invokeAll() across the 4-worker thread pool
        List<Future<CaseAuditResponse.EvidenceAuditItem>> futures;
        try {
            futures = labVerificationExecutor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Batch case integrity audit was interrupted", e);
        }

        List<CaseAuditResponse.EvidenceAuditItem> auditResults = new ArrayList<>();
        int verifiedCount = 0;
        int compromisedCount = 0;

        for (int i = 0; i < futures.size(); i++) {
            try {
                CaseAuditResponse.EvidenceAuditItem result = futures.get(i).get();
                auditResults.add(result);

                if (result.getStatus() == LabIntegrityStatus.VERIFIED) {
                    verifiedCount++;
                } else {
                    compromisedCount++;
                    EvidenceItemDto item = items.get(i);
                    caseServiceClient.updateEvidenceStatus(item.getId(), EvidenceStatus.SEQUESTERED,
                            "BATCH AUDIT: Hash mismatch detected. Evidence quarantined.", jwt);

                    // Publish Kafka tamper event for distributed quarantine
                    vaultKafkaProducer.publishEvidenceTampered(
                            item.getId(),
                            item.getSha256Checksum(),
                            result.getComputedHash(),
                            currentUserId,
                            "Tamper detected during parallel batch case audit"
                    );

                    ChainOfCustodyLog auditAlert = ChainOfCustodyLog.builder()
                            .evidenceId(item.getId())
                            .actorUserId(currentUserId)
                            .actionType(CustodyActionType.ANALYZE)
                            .notes("BATCH INTEGRITY AUDIT ALERT: Tamper detected during parallel case audit. Evidence quarantined.")
                            .hashAtEvent(result.getComputedHash())
                            .build();
                    custodyLogRepository.save(auditAlert);
                }
            } catch (Exception e) {
                log.error("Failed to retrieve audit result for evidence", e);
            }
        }

        long totalDurationMs = System.currentTimeMillis() - batchStartTime;
        log.info("[Main-Thread: {}] Batch audit complete for case #{} in {} ms. Verified: {}, Compromised: {}",
                Thread.currentThread().getName(), legalCase.getCaseNumber(), totalDurationMs, verifiedCount, compromisedCount);

        CaseAuditResponse response = CaseAuditResponse.builder()
                .caseId(caseId)
                .caseNumber(legalCase.getCaseNumber())
                .caseTitle(legalCase.getCaseTitle())
                .totalEvidenceAudited(items.size())
                .verifiedCount(verifiedCount)
                .compromisedCount(compromisedCount)
                .totalDurationMs(totalDurationMs)
                .auditItems(auditResults)
                .auditedAt(LocalDateTime.now())
                .build();

        if (compromisedCount > 0) {
            notificationService.broadcast("CASE_AUDIT_COMPROMISED", response);
        } else {
            notificationService.broadcast("CASE_AUDIT_COMPLETED", response);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public CustodyReservationResponse getReservationById(Long id, String jwt) {
        CustodyReservation res = findReservationById(id);
        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(res.getEvidenceId(), jwt);
        UserSummaryDto examiner = authServiceClient.getUserById(res.getExaminerId());
        return mapToResponse(res, evidence, examiner);
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForEvidence(Long evidenceId, Pageable pageable, String jwt) {
        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(evidenceId, jwt);
        return reservationRepository.findByEvidenceId(evidenceId, pageable)
                .map(res -> {
                    UserSummaryDto examiner = authServiceClient.getUserById(res.getExaminerId());
                    return mapToResponse(res, evidence, examiner);
                });
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForExaminer(Long examinerId, Pageable pageable, String jwt) {
        UserSummaryDto examiner = authServiceClient.getUserById(examinerId);
        return reservationRepository.findByExaminerId(examinerId, pageable)
                .map(res -> {
                    EvidenceItemDto evidence = caseServiceClient.getEvidenceById(res.getEvidenceId(), jwt);
                    return mapToResponse(res, evidence, examiner);
                });
    }

    private CustodyReservation findReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custody reservation not found with ID: " + id));
    }

    private CustodyReservationResponse mapToResponse(CustodyReservation reservation, EvidenceItemDto evidence, UserSummaryDto examiner) {
        return CustodyReservationResponse.builder()
                .id(reservation.getId())
                .evidenceItemId(reservation.getEvidenceId())
                .evidenceTrackingCode(evidence != null ? evidence.getTrackingCode() : null)
                .evidenceName(evidence != null ? evidence.getName() : null)
                .examinerId(reservation.getExaminerId())
                .examinerName(examiner != null ? examiner.getFullName() : null)
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .notes(reservation.getNotes())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    private ForensicLabReportResponse mapToReportResponse(ForensicLabReport report, EvidenceItemDto evidence, UserSummaryDto examiner) {
        return ForensicLabReportResponse.builder()
                .id(report.getId())
                .custodyReservationId(report.getCustodyReservation() != null ? report.getCustodyReservation().getId() : null)
                .evidenceItemId(report.getEvidenceItemId())
                .evidenceTrackingCode(evidence != null ? evidence.getTrackingCode() : null)
                .evidenceName(evidence != null ? evidence.getName() : null)
                .examinerId(report.getExaminerId())
                .examinerName(examiner != null ? examiner.getFullName() : null)
                .preExamHash(report.getPreExamHash())
                .postExamHash(report.getPostExamHash())
                .integrityStatus(report.getIntegrityStatus())
                .examinerFindings(report.getExaminerFindings())
                .verifiedByThread(report.getVerifiedByThread())
                .verificationDurationMs(report.getVerificationDurationMs())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private record VerificationResult(String computedHash, String threadName, long durationMs) {}
}
