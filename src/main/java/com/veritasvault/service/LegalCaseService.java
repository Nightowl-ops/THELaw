package com.veritasvault.service;

import com.veritasvault.dto.request.CreateCaseRequest;
import com.veritasvault.dto.response.CaseResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LegalCaseService {

    private final LegalCaseRepository legalCaseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        // 1. Guard against duplicate case numbers
        if (legalCaseRepository.existsByCaseNumber(request.getCaseNumber())) {
            throw new BadRequestException("Case number '" + request.getCaseNumber() + "' already exists");
        }

        // 2. Validate Lead Attorney existence & role
        User attorney = userRepository.findById(request.getLeadAttorneyId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead attorney not found with ID: " + request.getLeadAttorneyId()));

        if (attorney.getRole() != Role.ROLE_ATTORNEY && attorney.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException("User ID " + request.getLeadAttorneyId() + " must have ROLE_ATTORNEY or ROLE_ADMIN");
        }

        // 3. Validate Client existence & role (optional field)
        User client = null;
        if (request.getClientId() != null) {
            client = userRepository.findById(request.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with ID: " + request.getClientId()));

            if (client.getRole() != Role.ROLE_CLIENT) {
                throw new BadRequestException("User ID " + request.getClientId() + " must have ROLE_CLIENT");
            }
        }

        // 4. Construct and persist LegalCase
        LegalCase legalCase = LegalCase.builder()
                .caseNumber(request.getCaseNumber())
                .caseTitle(request.getCaseTitle())
                .caseType(request.getCaseType())
                .status(CaseStatus.ACTIVE)
                .leadAttorney(attorney)
                .client(client)
                .opposingPartyName(request.getOpposingPartyName())
                .opposingCounselName(request.getOpposingCounselName())
                .opposingCounselEmail(request.getOpposingCounselEmail())
                .build();

        LegalCase saved = legalCaseRepository.save(legalCase);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(Long id) {
        LegalCase legalCase = legalCaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + id));
        return mapToResponse(legalCase);
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

    private CaseResponse mapToResponse(LegalCase legalCase) {
        return CaseResponse.builder()
                .id(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .caseTitle(legalCase.getCaseTitle())
                .caseType(legalCase.getCaseType())
                .status(legalCase.getStatus())
                .leadAttorneyId(legalCase.getLeadAttorney().getId())
                .leadAttorneyName(legalCase.getLeadAttorney().getFullName())
                .leadAttorneyEmail(legalCase.getLeadAttorney().getEmail())
                .clientId(legalCase.getClient() != null ? legalCase.getClient().getId() : null)
                .clientName(legalCase.getClient() != null ? legalCase.getClient().getFullName() : null)
                .clientEmail(legalCase.getClient() != null ? legalCase.getClient().getEmail() : null)
                .opposingPartyName(legalCase.getOpposingPartyName())
                .opposingCounselName(legalCase.getOpposingCounselName())
                .opposingCounselEmail(legalCase.getOpposingCounselEmail())
                .createdAt(legalCase.getCreatedAt())
                .updatedAt(legalCase.getUpdatedAt())
                .build();
    }
}