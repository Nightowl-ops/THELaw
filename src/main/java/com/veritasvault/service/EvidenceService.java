package com.veritasvault.service;

import com.veritasvault.dto.request.EvidenceUploadRequest;
import com.veritasvault.dto.response.EvidenceResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.ChainOfCustodyLog;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CustodyActionType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import com.veritasvault.util.ChecksumUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing evidence intake, cryptographic verification,
 * storage coordination, and automated chain of custody auditing.
 */
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final EvidenceItemRepository evidenceItemRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final UserRepository userRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final FileStorageService fileStorageService;

    /**
     * Uploads, hashes, and registers a new evidence item with an initial INGEST custody record.
     */
    @Transactional
    public EvidenceResponse uploadEvidence(
            MultipartFile file,
            EvidenceUploadRequest request,
            Long currentUserId
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be null or empty");
        }

        LegalCase legalCase = legalCaseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + request.getCaseId()));

        User uploader = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        // 1. Calculate SHA-256 fingerprint before saving
        String sha256Checksum;
        try {
            sha256Checksum = ChecksumUtils.calculateSha256(file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file stream for SHA-256 checksum generation");
        }

        // 2. Store binary asset on disk
        String subfolder = "evidence/case-" + legalCase.getId();
        String relativeStoragePath = fileStorageService.storeFile(file, subfolder);

        // 3. Generate unique court tracking code
        String trackingCode = generateTrackingCode();

        // 4. Construct and persist EvidenceItem entity
        EvidenceItem evidenceItem = EvidenceItem.builder()
                .itemTrackingCode(trackingCode)
                .legalCase(legalCase)
                .name(request.getName())
                .description(request.getDescription())
                .evidenceType(request.getEvidenceType())
                .storageUrl(relativeStoragePath)
                .fileSizeBytes(file.getSize())
                .sha256Hash(sha256Checksum)
                .status(EvidenceStatus.INGESTED)
                .sourceOrigin(request.getSourceOrigin())
                .seizingOfficerName(request.getSeizingOfficerName())
                .officerBadgeNumber(request.getOfficerBadgeNumber())
                .lawEnforcementAgency(request.getLawEnforcementAgency())
                .policeIncidentReportNum(request.getPoliceIncidentReportNum())
                .uploadedBy(uploader)
                .build();

        EvidenceItem savedEvidence = evidenceItemRepository.save(evidenceItem);

        // 5. Create immutable initial INGEST record in Chain of Custody
        ChainOfCustodyLog initialLog = ChainOfCustodyLog.builder()
                .evidenceItem(savedEvidence)
                .actor(uploader)
                .actionType(CustodyActionType.INGEST)
                .notes("Initial evidence intake, cryptographic hashing, and repository registration. Baseline SHA-256: " + sha256Checksum)
                .hashAtEvent(sha256Checksum)
                .build();

        custodyLogRepository.save(initialLog);

        return mapToResponse(savedEvidence, file.getOriginalFilename(), file.getContentType());
    }


    @Transactional(readOnly = true)
    public EvidenceResponse getEvidenceById(Long id) {
        EvidenceItem evidence = findEvidenceById(id);
        return mapToResponse(evidence, null, null);
    }


    @Transactional(readOnly = true)
    public EvidenceResponse getEvidenceByTrackingCode(String trackingCode) {
        EvidenceItem evidence = evidenceItemRepository.findByItemTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found with tracking code: " + trackingCode));
        return mapToResponse(evidence, null, null);
    }


    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidenceByCaseId(Long caseId) {
        if (!legalCaseRepository.existsById(caseId)) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }
        return evidenceItemRepository.findByLegalCaseId(caseId)
                .stream()
                .map(item -> mapToResponse(item, null, null))
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public Resource loadEvidenceFile(Long id) {
        EvidenceItem evidence = findEvidenceById(id);
        return fileStorageService.loadAsResource(evidence.getStorageUrl());
    }


    @Transactional
    public EvidenceResponse updateStatus(
            Long id,
            EvidenceStatus newStatus,
            String notes,
            Long currentUserId
    ) {
        EvidenceItem evidence = findEvidenceById(id);
        User actor = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        EvidenceStatus previousStatus = evidence.getStatus();
        evidence.setStatus(newStatus);
        EvidenceItem updated = evidenceItemRepository.save(evidence);

        CustodyActionType action = mapStatusToAction(newStatus);

        ChainOfCustodyLog auditLog = ChainOfCustodyLog.builder()
                .evidenceItem(updated)
                .actor(actor)
                .actionType(action)
                .notes((notes != null && !notes.isBlank() ? notes : "Status updated from " + previousStatus + " to " + newStatus))
                .hashAtEvent(updated.getSha256Hash())
                .build();

        custodyLogRepository.save(auditLog);

        return mapToResponse(updated, null, null);
    }

    // --- Helper Methods ---

    private EvidenceItem findEvidenceById(Long id) {
        return evidenceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence item not found with ID: " + id));
    }

    private synchronized String generateTrackingCode() {
        int currentYear = Year.now().getValue();
        long count = evidenceItemRepository.count() + 1;
        return String.format("EV-%d-%04d", currentYear, count);
    }

    private CustodyActionType mapStatusToAction(EvidenceStatus status) {
        if (status == null) {
            return CustodyActionType.ANALYZE;
        }
        return switch (status) {
            case INGESTED -> CustodyActionType.INGEST;
            case AVAILABLE -> CustodyActionType.CHECK_IN;
            case RESERVED, IN_LAB -> CustodyActionType.CHECK_OUT;
            case ADMITTED -> CustodyActionType.COURT_SUBMISSION;
            case SEQUESTERED -> CustodyActionType.ANALYZE;
            case STAMPED -> CustodyActionType.REDACT;
        };
    }

    public EvidenceResponse mapToResponse(EvidenceItem item, String originalFileName, String mimeType) {
        return EvidenceResponse.builder()
                .id(item.getId())
                .trackingCode(item.getItemTrackingCode())
                .caseId(item.getLegalCase() != null ? item.getLegalCase().getId() : null)
                .caseNumber(item.getLegalCase() != null ? item.getLegalCase().getCaseNumber() : null)
                .name(item.getName())
                .description(item.getDescription())
                .evidenceType(item.getEvidenceType())
                .status(item.getStatus())
                .fileStoragePath(item.getStorageUrl())
                .originalFileName(originalFileName)
                .mimeType(mimeType)
                .fileSizeBytes(item.getFileSizeBytes())
                .sha256Checksum(item.getSha256Hash())
                .sourceOrigin(item.getSourceOrigin())
                .seizingOfficerName(item.getSeizingOfficerName())
                .officerBadgeNumber(item.getOfficerBadgeNumber())
                .lawEnforcementAgency(item.getLawEnforcementAgency())
                .policeIncidentReportNum(item.getPoliceIncidentReportNum())
                .uploadedById(item.getUploadedBy() != null ? item.getUploadedBy().getId() : null)
                .uploadedByName(item.getUploadedBy() != null ? item.getUploadedBy().getFullName() : null)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}