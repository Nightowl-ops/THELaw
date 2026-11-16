package com.veritasvault.forensicvault.service;

import com.veritasvault.forensicvault.client.AuthServiceClient;
import com.veritasvault.forensicvault.client.CaseServiceClient;
import com.veritasvault.forensicvault.client.EvidenceItemDto;
import com.veritasvault.forensicvault.client.UserSummaryDto;
import com.veritasvault.forensicvault.dto.response.ChainOfCustodyResponse;
import com.veritasvault.forensicvault.model.ChainOfCustodyLog;
import com.veritasvault.forensicvault.repository.ChainOfCustodyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChainOfCustodyService {

    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final CaseServiceClient caseServiceClient;
    private final AuthServiceClient authServiceClient;

    @Transactional(readOnly = true)
    public List<ChainOfCustodyResponse> getAuditTrailForEvidence(Long evidenceId, String jwt) {
        EvidenceItemDto evidence = caseServiceClient.getEvidenceById(evidenceId, jwt);

        return custodyLogRepository.findByEvidenceIdOrderByLoggedAtAsc(evidenceId)
                .stream()
                .map(logEntry -> {
                    UserSummaryDto actor = authServiceClient.getUserById(logEntry.getActorUserId());
                    return mapToResponse(logEntry, evidence, actor);
                })
                .collect(Collectors.toList());
    }

    private ChainOfCustodyResponse mapToResponse(ChainOfCustodyLog logEntry, EvidenceItemDto evidence, UserSummaryDto actor) {
        return ChainOfCustodyResponse.builder()
                .id(logEntry.getId())
                .evidenceId(logEntry.getEvidenceId())
                .evidenceTrackingCode(evidence != null ? evidence.getTrackingCode() : null)
                .evidenceName(evidence != null ? evidence.getName() : null)
                .actorId(logEntry.getActorUserId())
                .actorName(actor != null ? actor.getFullName() : "User #" + logEntry.getActorUserId())
                .actorEmail(actor != null ? actor.getEmail() : null)
                .actionType(logEntry.getActionType())
                .notes(logEntry.getNotes())
                .hashAtEvent(logEntry.getHashAtEvent())
                .loggedAt(logEntry.getLoggedAt())
                .build();
    }
}
