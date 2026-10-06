package com.veritasvault.service;

import com.veritasvault.dto.request.CreateDiscoveryRequest;
import com.veritasvault.dto.response.DiscoveryResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.BinderExhibit;
import com.veritasvault.model.DiscoveryAccessLog;
import com.veritasvault.model.DiscoveryProduction;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing external discovery production packages, time-limited token links,
 * and immutable access telemetry for legal Proof of Service.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryProductionService {

    private final DiscoveryProductionRepository discoveryProductionRepository;
    private final DiscoveryAccessLogRepository discoveryAccessLogRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final ExhibitBinderRepository exhibitBinderRepository;
    private final BinderExhibitRepository binderExhibitRepository;
    private final UserRepository userRepository;

    @Value("${app.server.base-url:http://localhost:8085}")
    private String baseUrl;

    /**
     * Creates a discovery production package tied to an exhibit binder and generates
     * a secure, time-expiring external access token.
     */
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

        // Generate non-guessable secure token
        String accessToken = UUID.randomUUID().toString();

        DiscoveryProduction production = DiscoveryProduction.builder()
                .legalCase(legalCase)
                .binder(binder)
                .targetPartyName(request.getRecipientParty().trim())
                .accessToken(accessToken)
                .isWatermarked(true)
                .expiresAt(request.getExpiresAt())
                .createdBy(creator)
                .build();

        DiscoveryProduction saved = discoveryProductionRepository.save(production);

        // Advance binder status to SERVED upon production
        binder.setStatus(BinderStatus.SERVED);
        exhibitBinderRepository.save(binder);

        return mapToResponse(saved);
    }

    /**
     * Validates an external token and returns discovery package details.
     * Records access telemetry (IP and User-Agent) as courtroom Proof of Service.
     */
    @Transactional
    public DiscoveryResponse accessProductionByToken(String accessToken, String ipAddress, String userAgent) {
        DiscoveryProduction production = discoveryProductionRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unrecognized discovery access token"));

        // Validate expiration
        if (LocalDateTime.now().isAfter(production.getExpiresAt())) {
            throw new BadRequestException("This discovery package link has expired");
        }

        // Log access audit trail
        DiscoveryAccessLog log = DiscoveryAccessLog.builder()
                .production(production)
                .ipAddress(ipAddress != null ? ipAddress : "UNKNOWN")
                .userAgent(userAgent != null ? userAgent : "UNKNOWN")
                .downloadedFileName(production.getBinder().getBinderName() + "-bundle.pdf")
                .build();

        discoveryAccessLogRepository.save(log);

        return mapToResponse(production);
    }

    @Transactional(readOnly = true)
    public DiscoveryResponse getProductionById(Long id) {
        DiscoveryProduction production = discoveryProductionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discovery production not found with ID: " + id));
        return mapToResponse(production);
    }

    @Transactional(readOnly = true)
    public List<DiscoveryResponse> getProductionsByCaseId(Long caseId) {
        if (!legalCaseRepository.existsById(caseId)) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }
        return discoveryProductionRepository.findByLegalCaseId(caseId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- Helper Mapping Methods ---

    private DiscoveryResponse mapToResponse(DiscoveryProduction production) {
        String downloadUrl = baseUrl + "/api/discovery/download/" + production.getAccessToken();

        List<BinderExhibit> exhibits = binderExhibitRepository
                .findByBinderIdOrderByBatesStartNumAsc(production.getBinder().getId());

        int totalDownloads = discoveryAccessLogRepository
                .findByProductionIdOrderByAccessedAtDesc(production.getId())
                .size();

        return DiscoveryResponse.builder()
                .id(production.getId())
                .caseId(production.getLegalCase().getId())
                .caseNumber(production.getLegalCase().getCaseNumber())
                .title(production.getBinder().getBinderName())
                .description("Discovery bundle served to " + production.getTargetPartyName())
                .accessToken(production.getAccessToken())
                .downloadUrl(downloadUrl)
                .recipientParty(production.getTargetPartyName())
                .expiresAt(production.getExpiresAt())
                .downloadCount(totalDownloads)
                .isRevoked(LocalDateTime.now().isAfter(production.getExpiresAt()))
                .createdById(production.getCreatedBy().getId())
                .createdByName(production.getCreatedBy().getFullName())
                .createdAt(production.getCreatedAt())
                .build();
    }
}