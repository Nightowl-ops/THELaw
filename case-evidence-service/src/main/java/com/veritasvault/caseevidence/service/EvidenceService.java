package com.veritasvault.caseevidence.service;

import com.veritasvault.caseevidence.dto.request.EvidenceUploadRequest;
import com.veritasvault.caseevidence.dto.response.EvidenceResponse;
import com.veritasvault.caseevidence.exception.BadRequestException;
import com.veritasvault.caseevidence.exception.ResourceNotFoundException;
import com.veritasvault.caseevidence.kafka.EvidenceKafkaProducer;
import com.veritasvault.caseevidence.kafka.event.EvidenceIngestedEvent;
import com.veritasvault.caseevidence.model.EvidenceItem;
import com.veritasvault.caseevidence.model.LegalCase;
import com.veritasvault.caseevidence.model.enums.EvidenceStatus;
import com.veritasvault.caseevidence.model.enums.EvidenceType;
import com.veritasvault.caseevidence.repository.EvidenceItemRepository;
import com.veritasvault.caseevidence.repository.LegalCaseRepository;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.util.ChecksumUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final EvidenceItemRepository evidenceItemRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final FileStorageService fileStorageService;
    private final EvidenceKafkaProducer evidenceKafkaProducer;

    @Transactional
    public EvidenceResponse uploadEvidence(
            MultipartFile file,
            EvidenceUploadRequest request,
            UserPrincipal currentUser
    ) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be null or empty");
        }

        LegalCase legalCase = legalCaseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + request.getCaseId()));

        Long uploaderId = currentUser != null ? currentUser.getUserId() : 1L;

        String sha256Checksum;
        try {
            sha256Checksum = ChecksumUtils.calculateSha256(file.getInputStream());
        } catch (IOException e) {
            throw new BadRequestException("Failed to read file stream for SHA-256 checksum generation");
        }

        String subfolder = "evidence/case-" + legalCase.getId();
        String relativeStoragePath = fileStorageService.storeFile(file, subfolder);

        String trackingCode = generateTrackingCode();

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
                .uploadedByUserId(uploaderId)
                .build();

        EvidenceItem savedEvidence = evidenceItemRepository.save(evidenceItem);

        // Broadcast to Kafka so Forensic Vault can index new intake
        EvidenceIngestedEvent event = EvidenceIngestedEvent.builder()
                .evidenceId(savedEvidence.getId())
                .itemTrackingCode(savedEvidence.getItemTrackingCode())
                .caseId(legalCase.getId())
                .sha256Hash(savedEvidence.getSha256Hash())
                .fileSizeBytes(savedEvidence.getFileSizeBytes())
                .uploadedByUserId(uploaderId)
                .timestamp(LocalDateTime.now())
                .build();
        evidenceKafkaProducer.publishEvidenceIngested(event);

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
    public Page<EvidenceResponse> getEvidenceByCaseId(Long caseId, EvidenceStatus status, EvidenceType type, Pageable pageable) {
        if (!legalCaseRepository.existsById(caseId)) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }
        return evidenceItemRepository.findByCaseWithFilters(caseId, status, type, pageable)
                .map(item -> mapToResponse(item, null, null));
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
            UserPrincipal currentUser
    ) {
        EvidenceItem evidence = findEvidenceById(id);
        EvidenceStatus previousStatus = evidence.getStatus();

        if (!isValidStatusTransition(previousStatus, newStatus)) {
            throw new BadRequestException("Illegal evidence status transition from " + previousStatus + " to " + newStatus);
        }

        evidence.setStatus(newStatus);
        EvidenceItem updated = evidenceItemRepository.save(evidence);
        return mapToResponse(updated, null, null);
    }

    public static boolean isValidStatusTransition(EvidenceStatus from, EvidenceStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return switch (from) {
            case INGESTED -> to == EvidenceStatus.AVAILABLE;
            case AVAILABLE -> to == EvidenceStatus.RESERVED || to == EvidenceStatus.SEQUESTERED || to == EvidenceStatus.STAMPED || to == EvidenceStatus.ADMITTED;
            case RESERVED -> to == EvidenceStatus.IN_LAB || to == EvidenceStatus.AVAILABLE;
            case IN_LAB -> to == EvidenceStatus.AVAILABLE || to == EvidenceStatus.STAMPED || to == EvidenceStatus.SEQUESTERED;
            case STAMPED -> to == EvidenceStatus.AVAILABLE || to == EvidenceStatus.ADMITTED;
            case SEQUESTERED -> to == EvidenceStatus.AVAILABLE;
            case ADMITTED -> false;
        };
    }

    private EvidenceItem findEvidenceById(Long id) {
        return evidenceItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence item not found with ID: " + id));
    }

    private synchronized String generateTrackingCode() {
        int currentYear = Year.now().getValue();
        long count = evidenceItemRepository.count() + 1;
        return String.format("EV-%d-%04d", currentYear, count);
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
                .uploadedById(item.getUploadedByUserId())
                .uploadedByName("User #" + item.getUploadedByUserId())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    @Transactional
    public void simulateTampering(Long evidenceId) {
        EvidenceItem evidence = findEvidenceById(evidenceId);
        fileStorageService.appendTamperByte(evidence.getStorageUrl());
    }
}
