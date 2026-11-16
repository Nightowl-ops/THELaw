package com.veritasvault.caseevidence.service;

import com.veritasvault.caseevidence.client.AuthServiceClient;
import com.veritasvault.caseevidence.client.UserSummaryDto;
import com.veritasvault.caseevidence.dto.request.CreateCaseRequest;
import com.veritasvault.caseevidence.dto.response.CaseResponse;
import com.veritasvault.caseevidence.exception.BadRequestException;
import com.veritasvault.caseevidence.exception.ConflictException;
import com.veritasvault.caseevidence.exception.ResourceNotFoundException;
import com.veritasvault.caseevidence.model.LegalCase;
import com.veritasvault.caseevidence.model.enums.CaseStatus;
import com.veritasvault.caseevidence.model.enums.CaseType;
import com.veritasvault.caseevidence.model.enums.Role;
import com.veritasvault.caseevidence.repository.LegalCaseRepository;
import com.veritasvault.caseevidence.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegalCaseService {

    private final LegalCaseRepository legalCaseRepository;
    private final AuthServiceClient authServiceClient;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request, UserPrincipal currentUser) {
        if (legalCaseRepository.existsByCaseNumber(request.getCaseNumber())) {
            throw new ConflictException("Case number '" + request.getCaseNumber() + "' already exists");
        }

        Long leadAttorneyId = request.getLeadAttorneyId();
        if (leadAttorneyId == null && currentUser != null) {
            leadAttorneyId = currentUser.getUserId();
        }
        if (leadAttorneyId == null) {
            throw new BadRequestException("Lead attorney ID is required");
        }

        // Synchronous Inter-Service HTTP Verification: Verify Attorney exists in Auth Service
        UserSummaryDto attorney = authServiceClient.getUserById(leadAttorneyId);
        if (attorney != null && !Role.ROLE_ATTORNEY.name().equals(attorney.getRole()) && !Role.ROLE_ADMIN.name().equals(attorney.getRole())) {
            throw new BadRequestException("User ID " + leadAttorneyId + " must have ROLE_ATTORNEY or ROLE_ADMIN");
        }

        // Synchronous Inter-Service HTTP Verification: Verify Client exists in Auth Service (if assigned)
        if (request.getClientId() != null) {
            UserSummaryDto client = authServiceClient.getUserById(request.getClientId());
            if (client != null && !Role.ROLE_CLIENT.name().equals(client.getRole())) {
                throw new BadRequestException("User ID " + request.getClientId() + " must have ROLE_CLIENT");
            }
        }

        LegalCase legalCase = LegalCase.builder()
                .caseNumber(request.getCaseNumber())
                .caseTitle(request.getCaseTitle())
                .caseType(request.getCaseType())
                .status(CaseStatus.ACTIVE)
                .leadAttorneyId(leadAttorneyId)
                .clientId(request.getClientId())
                .opposingPartyName(request.getOpposingPartyName())
                .opposingCounselName(request.getOpposingCounselName())
                .opposingCounselEmail(request.getOpposingCounselEmail())
                .build();

        LegalCase saved = legalCaseRepository.save(legalCase);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id, UserPrincipal currentUser) {
        LegalCase legalCase = legalCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + id));

        if (currentUser != null && Role.ROLE_CLIENT.name().equals(currentUser.getRole())) {
            if (legalCase.getClientId() == null || !legalCase.getClientId().equals(currentUser.getUserId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to view this legal case");
            }
        }

        return mapToResponse(legalCase);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id) {
        LegalCase legalCase = legalCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + id));
        return mapToResponse(legalCase);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> getCases(CaseStatus status, CaseType type, String search, UserPrincipal currentUser, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : "";

        if (currentUser != null && Role.ROLE_CLIENT.name().equals(currentUser.getRole())) {
            return legalCaseRepository.searchCasesByClient(currentUser.getUserId(), status, type, query, pageable)
                    .map(this::mapToResponse);
        }

        return legalCaseRepository.searchCases(status, type, query, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getAllCases() {
        return legalCaseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getCasesForAttorney(Long attorneyId) {
        return legalCaseRepository.findByLeadAttorneyId(attorneyId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CaseResponse updateCaseStatus(Long id, CaseStatus newStatus) {
        LegalCase legalCase = legalCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + id));

        legalCase.setStatus(newStatus);
        LegalCase updated = legalCaseRepository.save(legalCase);
        return mapToResponse(updated);
    }

    public CaseResponse mapToResponse(LegalCase legalCase) {
        String attorneyName = "Lead Attorney #" + legalCase.getLeadAttorneyId();
        String attorneyEmail = "attorney" + legalCase.getLeadAttorneyId() + "@veritasvault.com";
        UserSummaryDto attorney = authServiceClient.getUserById(legalCase.getLeadAttorneyId());
        if (attorney != null) {
            attorneyName = attorney.getFullName();
            attorneyEmail = attorney.getEmail();
        }

        String clientName = legalCase.getClientId() != null ? "Client #" + legalCase.getClientId() : null;
        String clientEmail = legalCase.getClientId() != null ? "client" + legalCase.getClientId() + "@veritasvault.com" : null;
        if (legalCase.getClientId() != null) {
            UserSummaryDto client = authServiceClient.getUserById(legalCase.getClientId());
            if (client != null) {
                clientName = client.getFullName();
                clientEmail = client.getEmail();
            }
        }

        return CaseResponse.builder()
                .id(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .caseTitle(legalCase.getCaseTitle())
                .caseType(legalCase.getCaseType())
                .status(legalCase.getStatus())
                .leadAttorneyId(legalCase.getLeadAttorneyId())
                .leadAttorneyName(attorneyName)
                .leadAttorneyEmail(attorneyEmail)
                .clientId(legalCase.getClientId())
                .clientName(clientName)
                .clientEmail(clientEmail)
                .opposingPartyName(legalCase.getOpposingPartyName())
                .opposingCounselName(legalCase.getOpposingCounselName())
                .opposingCounselEmail(legalCase.getOpposingCounselEmail())
                .createdAt(legalCase.getCreatedAt())
                .updatedAt(legalCase.getUpdatedAt())
                .build();
    }
}
