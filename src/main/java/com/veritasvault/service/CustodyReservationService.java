package com.veritasvault.service;

import com.veritasvault.dto.request.CreateLabReportRequest;
import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.dto.response.ForensicLabReportResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ConflictException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.ChainOfCustodyLog;
import com.veritasvault.model.CustodyReservation;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.ForensicLabReport;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CustodyActionType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.LabIntegrityStatus;
import com.veritasvault.model.enums.ReservationStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.CustodyReservationRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.ForensicLabReportRepository;
import com.veritasvault.repository.UserRepository;
import com.veritasvault.util.ChecksumUtils;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import com.veritasvault.dto.response.CaseAuditResponse;
import com.veritasvault.model.LegalCase;
import com.veritasvault.repository.LegalCaseRepository;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustodyReservationService {

    private final CustodyReservationRepository reservationRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final UserRepository userRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final ForensicLabReportRepository labReportRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

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
    public CustodyReservationResponse createReservation(CustodyReservationRequest request, Long examinerId) {
        log.info("[Thread: {}] Attempting to book custody reservation for evidence item ID: {}",
                Thread.currentThread().getName(), request.getEvidenceItemId());

        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BadRequestException("Reservation start time must be strictly before end time");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cannot create a reservation for a date or time in the past");
        }

        // Pessimistic write lock: blocks concurrent transactions in SQL (SELECT ... FOR UPDATE)
        EvidenceItem evidence = evidenceItemRepository.findByIdWithPessimisticLock(request.getEvidenceItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Evidence item not found with ID: " + request.getEvidenceItemId()));

        if (evidence.getStatus() == EvidenceStatus.SEQUESTERED || evidence.getStatus() == EvidenceStatus.ADMITTED) {
            throw new BadRequestException("Evidence item cannot be reserved: current status is " + evidence.getStatus());
        }

        User examiner = userRepository.findById(examinerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + examinerId));

        if (examiner.getRole() != Role.ROLE_FORENSIC_EXAMINER && examiner.getRole() != Role.ROLE_ADMIN) {
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
                .evidenceItem(evidence)
                .examiner(examiner)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .notes(request.getNotes())
                .build();

        CustodyReservation savedReservation = reservationRepository.save(reservation);

        evidence.setStatus(EvidenceStatus.RESERVED);
        evidenceItemRepository.save(evidence);

        CustodyReservationResponse response = mapToResponse(savedReservation);
        notificationService.broadcast("CUSTODY_RESERVATION_CREATED", response);

        log.info("[Thread: {}] Successfully created reservation ID: {} for evidence ID: {}",
                Thread.currentThread().getName(), savedReservation.getId(), evidence.getId());
        return response;
    }

    @Transactional
    public CustodyReservationResponse cancelReservation(Long reservationId, Long currentUserId) {
        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

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

        EvidenceItem evidence = reservation.getEvidenceItem();
        if (evidence.getStatus() == EvidenceStatus.RESERVED) {
            evidence.setStatus(EvidenceStatus.AVAILABLE);
            evidenceItemRepository.save(evidence);
        }

        ChainOfCustodyLog cancellationLog = ChainOfCustodyLog.builder()
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Reservation #" + reservation.getId() + " cancelled. Evidence slot released.")
                .hashAtEvent(evidence.getSha256Hash())
                .build();
        custodyLogRepository.save(cancellationLog);

        CustodyReservationResponse response = mapToResponse(updated);
        notificationService.broadcast("CUSTODY_RESERVATION_CANCELLED", response);
        return response;
    }

    @Transactional
    public CustodyReservationResponse checkOutEvidence(Long reservationId, Long currentUserId) {
        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check out reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Only CONFIRMED reservations can be checked out");
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        CustodyReservation updated = reservationRepository.save(reservation);

        EvidenceItem evidence = reservation.getEvidenceItem();
        evidence.setStatus(EvidenceStatus.IN_LAB);
        evidenceItemRepository.save(evidence);

        ChainOfCustodyLog logEntry = ChainOfCustodyLog.builder()
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_OUT)
                .notes("Evidence checked out for lab examination under reservation #" + reservation.getId())
                .hashAtEvent(evidence.getSha256Hash())
                .build();
        custodyLogRepository.save(logEntry);

        CustodyReservationResponse response = mapToResponse(updated);
        notificationService.broadcast("EVIDENCE_CHECKED_OUT", response);
        return response;
    }

    /**
     * Multithreaded Check-In with parallel cryptographic SHA-256 hash verification.
     * Uses ExecutorService pool to execute the hash computation as an asynchronous Callable task,
     * comparing the pre-examination hash against the post-examination file state.
     */
    @Transactional
    public ForensicLabReportResponse checkInWithLabReport(Long reservationId, CreateLabReportRequest request, Long currentUserId) {
        log.info("[Main-Thread: {}] Initiating forensic check-in for reservation ID: {}",
                Thread.currentThread().getName(), reservationId);

        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check in reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE reservations can be checked in");
        }

        EvidenceItem evidence = reservation.getEvidenceItem();
        String preExamHash = evidence.getSha256Hash();

        // Submit Callable task to ExecutorService thread pool
        long startTime = System.currentTimeMillis();
        Future<VerificationResult> future = labVerificationExecutor.submit(() -> {
            String workerThread = Thread.currentThread().getName();
            log.info("[Worker-Thread: {}] Executing parallel cryptographic verification on evidence: {} ({})",
                    workerThread, evidence.getName(), evidence.getItemTrackingCode());

            String computedHash;
            try {
                if (evidence.getStorageUrl() != null && !evidence.getStorageUrl().isBlank() && fileStorageService != null) {
                    Resource resource = fileStorageService.loadAsResource(evidence.getStorageUrl());
                    if (resource != null && resource.exists() && resource.isReadable()) {
                        try (InputStream is = resource.getInputStream()) {
                            computedHash = ChecksumUtils.calculateSha256(is);
                        }
                    } else {
                        log.warn("[Worker-Thread: {}] Evidence file does not exist or is not readable: {}",
                                workerThread, evidence.getStorageUrl());
                        computedHash = "FILE_NOT_FOUND_OR_UNREADABLE";
                    }
                } else {
                    log.warn("[Worker-Thread: {}] No storage URL associated with evidence #{}",
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
            evidence.setStatus(EvidenceStatus.AVAILABLE);
        } else {
            integrityStatus = LabIntegrityStatus.COMPROMISED;
            evidence.setStatus(EvidenceStatus.SEQUESTERED);
            log.warn("FORENSIC ALERT: Hash mismatch on check-in for evidence #{}! Expected: {}, Computed: {}",
                    evidence.getId(), preExamHash, postExamHash);
        }

        evidenceItemRepository.save(evidence);

        reservation.setStatus(ReservationStatus.COMPLETED);
        CustodyReservation updatedReservation = reservationRepository.save(reservation);

        ForensicLabReport labReport = ForensicLabReport.builder()
                .custodyReservation(updatedReservation)
                .evidenceItem(evidence)
                .examiner(currentUser)
                .preExamHash(preExamHash)
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
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_IN)
                .notes(checkInNotes)
                .hashAtEvent(postExamHash)
                .build();
        custodyLogRepository.save(logEntry);

        ForensicLabReportResponse response = mapToReportResponse(savedReport);
        notificationService.broadcast(
                integrityStatus == LabIntegrityStatus.COMPROMISED ? "EVIDENCE_COMPROMISED" : "EVIDENCE_CHECKED_IN",
                response
        );

        return response;
    }

    @Transactional
    public CustodyReservationResponse checkInEvidence(Long reservationId, String checkInNotes, Long currentUserId) {
        return checkInEvidence(reservationId, checkInNotes, CustodyActionType.CHECK_IN, currentUserId);
    }

    @Transactional
    public CustodyReservationResponse checkInEvidence(Long reservationId, String checkInNotes, CustodyActionType actionType, Long currentUserId) {
        CreateLabReportRequest reportRequest = CreateLabReportRequest.builder()
                .notes(checkInNotes)
                .examinerFindings(checkInNotes)
                .build();
        checkInWithLabReport(reservationId, reportRequest, currentUserId);
        CustodyReservation reservation = findReservationById(reservationId);
        return mapToResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ForensicLabReportResponse getLabReportByReservationId(Long reservationId) {
        ForensicLabReport report = labReportRepository.findByCustodyReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Forensic lab report not found for reservation ID: " + reservationId));
        return mapToReportResponse(report);
    }

    /**
     * Batch Case Integrity Audit executing parallel cryptographic SHA-256 verifications
     * across all evidence items in a case concurrently using invokeAll() on the 4-worker thread pool.
     */
    @Transactional
    public CaseAuditResponse auditCaseEvidenceIntegrity(Long caseId, Long currentUserId) {
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + caseId));

        User auditor = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        if (auditor.getRole() != Role.ROLE_ATTORNEY && auditor.getRole() != Role.ROLE_FORENSIC_EXAMINER && auditor.getRole() != Role.ROLE_ADMIN) {
            throw new AccessDeniedException("Only attorneys, forensic examiners, or administrators can run case audits");
        }

        List<EvidenceItem> items = evidenceItemRepository.findByLegalCaseId(caseId);
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
        for (EvidenceItem item : items) {
            tasks.add(() -> {
                long itemStart = System.currentTimeMillis();
                String workerThread = Thread.currentThread().getName();
                String computedHash = null;
                LabIntegrityStatus status;

                try {
                    if (item.getStorageUrl() != null && !item.getStorageUrl().isBlank() && fileStorageService != null) {
                        Resource resource = fileStorageService.loadAsResource(item.getStorageUrl());
                        if (resource != null && resource.exists() && resource.isReadable()) {
                            try (InputStream is = resource.getInputStream()) {
                                computedHash = ChecksumUtils.calculateSha256(is);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[Worker-Thread: {}] Audit could not read evidence #{} ({}): {}",
                            workerThread, item.getId(), item.getItemTrackingCode(), ex.getMessage());
                }

                if (computedHash != null && item.getSha256Hash() != null && item.getSha256Hash().equalsIgnoreCase(computedHash)) {
                    status = LabIntegrityStatus.VERIFIED;
                } else {
                    status = LabIntegrityStatus.COMPROMISED;
                }

                long itemDuration = System.currentTimeMillis() - itemStart;
                log.info("[Worker-Thread: {}] Audited evidence #{} ({}) in {} ms. Status: {}",
                        workerThread, item.getId(), item.getItemTrackingCode(), itemDuration, status);

                return CaseAuditResponse.EvidenceAuditItem.builder()
                        .evidenceId(item.getId())
                        .trackingCode(item.getItemTrackingCode())
                        .name(item.getName())
                        .baselineHash(item.getSha256Hash())
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
                    EvidenceItem item = items.get(i);
                    item.setStatus(EvidenceStatus.SEQUESTERED);
                    evidenceItemRepository.save(item);

                    ChainOfCustodyLog auditAlert = ChainOfCustodyLog.builder()
                            .evidenceItem(item)
                            .actor(auditor)
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
    public CustodyReservationResponse getReservationById(Long id) {
        return mapToResponse(findReservationById(id));
    }

    @Transactional(readOnly = true)
    public List<CustodyReservationResponse> getReservationsForEvidence(Long evidenceId) {
        if (!evidenceItemRepository.existsById(evidenceId)) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId);
        }
        return reservationRepository.findByEvidenceItemId(evidenceId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForEvidence(Long evidenceId, Pageable pageable) {
        if (!evidenceItemRepository.existsById(evidenceId)) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId);
        }
        return reservationRepository.findByEvidenceItemId(evidenceId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CustodyReservationResponse> getReservationsForExaminer(Long examinerId) {
        if (!userRepository.existsById(examinerId)) {
            throw new ResourceNotFoundException("User not found with ID: " + examinerId);
        }
        return reservationRepository.findByExaminerId(examinerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForExaminer(Long examinerId, Pageable pageable) {
        if (!userRepository.existsById(examinerId)) {
            throw new ResourceNotFoundException("User not found with ID: " + examinerId);
        }
        return reservationRepository.findByExaminerId(examinerId, pageable)
                .map(this::mapToResponse);
    }

    private CustodyReservation findReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custody reservation not found with ID: " + id));
    }

    private CustodyReservationResponse mapToResponse(CustodyReservation reservation) {
        return CustodyReservationResponse.builder()
                .id(reservation.getId())
                .evidenceItemId(reservation.getEvidenceItem().getId())
                .evidenceTrackingCode(reservation.getEvidenceItem().getItemTrackingCode())
                .evidenceName(reservation.getEvidenceItem().getName())
                .examinerId(reservation.getExaminer().getId())
                .examinerName(reservation.getExaminer().getFullName())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .notes(reservation.getNotes())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    private ForensicLabReportResponse mapToReportResponse(ForensicLabReport report) {
        return ForensicLabReportResponse.builder()
                .id(report.getId())
                .custodyReservationId(report.getCustodyReservation() != null ? report.getCustodyReservation().getId() : null)
                .evidenceItemId(report.getEvidenceItem() != null ? report.getEvidenceItem().getId() : null)
                .evidenceTrackingCode(report.getEvidenceItem() != null ? report.getEvidenceItem().getItemTrackingCode() : null)
                .evidenceName(report.getEvidenceItem() != null ? report.getEvidenceItem().getName() : null)
                .examinerId(report.getExaminer() != null ? report.getExaminer().getId() : null)
                .examinerName(report.getExaminer() != null ? report.getExaminer().getFullName() : null)
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