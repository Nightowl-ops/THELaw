package com.veritasvault.service;

import com.veritasvault.dto.request.CreateDiscoveryRequest;
import com.veritasvault.dto.response.DiscoveryReceiptResponse;
import com.veritasvault.dto.response.DiscoveryResponse;
import com.veritasvault.dto.response.EvidenceResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.BinderExhibit;
import com.veritasvault.model.DiscoveryAccessLog;
import com.veritasvault.model.DiscoveryProduction;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.ExhibitBinder;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.BinderStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.BinderExhibitRepository;
import com.veritasvault.repository.DiscoveryAccessLogRepository;
import com.veritasvault.repository.DiscoveryProductionRepository;
import com.veritasvault.repository.ExhibitBinderRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscoveryProductionService {

    private final DiscoveryProductionRepository discoveryProductionRepository;
    private final DiscoveryAccessLogRepository discoveryAccessLogRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final ExhibitBinderRepository exhibitBinderRepository;
    private final BinderExhibitRepository binderExhibitRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final EvidenceService evidenceService;

    @Value("${app.server.base-url:http://localhost:8085}")
    private String baseUrl;

    @Transactional
    public DiscoveryResponse createProduction(CreateDiscoveryRequest request, Long binderId, Long currentUserId) {
        LegalCase legalCase = legalCaseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + request.getCaseId()));

        ExhibitBinder binder = exhibitBinderRepository.findById(binderId)
                .orElseThrow(() -> new ResourceNotFoundException("Exhibit binder not found with ID: " + binderId));

        if (!binder.getLegalCase().getId().equals(legalCase.getId())) {
            throw new BadRequestException("Binder #" + binderId + " does not belong to case #" + legalCase.getCaseNumber());
        }

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        if (creator.getRole() != Role.ROLE_ATTORNEY && creator.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException("Only attorneys or administrators can produce discovery packages");
        }

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Expiration date must be in the future");
        }

        String rawAccessToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawAccessToken);

        DiscoveryProduction production = DiscoveryProduction.builder()
                .legalCase(legalCase)
                .binder(binder)
                .targetPartyName(request.getRecipientParty().trim())
                .accessToken(rawAccessToken)
                .accessTokenHash(tokenHash)
                .isWatermarked(true)
                .expiresAt(request.getExpiresAt())
                .createdBy(creator)
                .build();

        DiscoveryProduction saved = discoveryProductionRepository.save(production);

        binder.setStatus(BinderStatus.SERVED);
        exhibitBinderRepository.save(binder);

        return mapToResponse(saved, rawAccessToken);
    }

    @Transactional
    public DiscoveryResponse accessProductionByToken(String rawAccessToken, String ipAddress, String userAgent) {
        DiscoveryProduction production = findByRawToken(rawAccessToken);

        if (LocalDateTime.now().isAfter(production.getExpiresAt())) {
            throw new BadRequestException("This discovery package link has expired");
        }

        DiscoveryAccessLog log = DiscoveryAccessLog.builder()
                .production(production)
                .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                .userAgent(userAgent != null ? userAgent : "UNKNOWN")
                .downloadedFileName(production.getBinder().getBinderName() + "-bundle.pdf")
                .build();

        discoveryAccessLogRepository.save(log);

        return mapToResponse(production, rawAccessToken);
    }

    @Transactional
    public DownloadBundle loadProductionBundle(String rawAccessToken, String ipAddress, String userAgent) {
        DiscoveryProduction production = findByRawToken(rawAccessToken);

        if (LocalDateTime.now().isAfter(production.getExpiresAt())) {
            throw new BadRequestException("This discovery package link has expired");
        }

        List<BinderExhibit> exhibits = binderExhibitRepository
                .findByBinderIdOrderByBatesStartNumAsc(production.getBinder().getId());

        if (exhibits.isEmpty()) {
            throw new ResourceNotFoundException("No exhibits attached to this discovery binder");
        }

        EvidenceItem primaryEvidence = exhibits.get(0).getEvidenceItem();
        Resource resource = fileStorageService.loadAsResource(primaryEvidence.getStorageUrl());
        String fileName = production.getBinder().getBinderName() + "-discovery-exhibit.bin";

        DiscoveryAccessLog log = DiscoveryAccessLog.builder()
                .production(production)
                .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                .userAgent(userAgent != null ? userAgent : "UNKNOWN")
                .downloadedFileName(fileName)
                .build();

        discoveryAccessLogRepository.save(log);

        return new DownloadBundle(resource, fileName);
    }

    @Transactional(readOnly = true)
    public DiscoveryResponse getProductionById(Long id) {
        DiscoveryProduction production = discoveryProductionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discovery production not found with ID: " + id));
        return mapToResponse(production, production.getAccessToken());
    }

    @Transactional(readOnly = true)
    public List<DiscoveryResponse> getProductionsByCaseId(Long caseId) {
        if (!legalCaseRepository.existsById(caseId)) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }
        return discoveryProductionRepository.findByLegalCaseId(caseId)
                .stream()
                .map(prod -> mapToResponse(prod, prod.getAccessToken()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoveryReceiptResponse> getReceipts(Long productionId) {
        if (!discoveryProductionRepository.existsById(productionId)) {
            throw new ResourceNotFoundException("Discovery production not found with ID: " + productionId);
        }

        return discoveryAccessLogRepository.findByProductionIdOrderByAccessedAtDesc(productionId)
                .stream()
                .map(log -> DiscoveryReceiptResponse.builder()
                        .id(log.getId())
                        .productionId(log.getProduction().getId())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .downloadedFileName(log.getDownloadedFileName())
                        .accessedAt(log.getAccessedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private DiscoveryProduction findByRawToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        return discoveryProductionRepository.findByAccessTokenHash(tokenHash)
                .or(() -> discoveryProductionRepository.findByAccessToken(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unrecognized discovery access token"));
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available on this JVM", e);
        }
    }

    private DiscoveryResponse mapToResponse(DiscoveryProduction production, String displayToken) {
        String tokenToUse = (displayToken != null) ? displayToken : production.getAccessToken();
        String downloadUrl = baseUrl + "/api/discovery/download/" + tokenToUse;

        int totalDownloads = discoveryAccessLogRepository
                .findByProductionIdOrderByAccessedAtDesc(production.getId())
                .size();

        List<EvidenceResponse> includedEvidence = binderExhibitRepository
                .findByBinderIdOrderByBatesStartNumAsc(production.getBinder().getId())
                .stream()
                .map(be -> evidenceService.mapToResponse(be.getEvidenceItem(), null, null))
                .collect(Collectors.toList());

        return DiscoveryResponse.builder()
                .id(production.getId())
                .caseId(production.getLegalCase().getId())
                .caseNumber(production.getLegalCase().getCaseNumber())
                .binderId(production.getBinder().getId())
                .title(production.getBinder().getBinderName())
                .description("Discovery bundle served to " + production.getTargetPartyName())
                .accessToken(tokenToUse)
                .downloadUrl(downloadUrl)
                .recipientParty(production.getTargetPartyName())
                .expiresAt(production.getExpiresAt())
                .downloadCount(totalDownloads)
                .isRevoked(LocalDateTime.now().isAfter(production.getExpiresAt()))
                .createdById(production.getCreatedBy().getId())
                .createdByName(production.getCreatedBy().getFullName())
                .evidenceItems(includedEvidence)
                .createdAt(production.getCreatedAt())
                .build();
    }

    public record DownloadBundle(Resource resource, String fileName) {}
}