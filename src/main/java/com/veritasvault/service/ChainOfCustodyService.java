package com.veritasvault.service;

import com.veritasvault.dto.response.ChainOfCustodyResponse;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.ChainOfCustodyLog;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChainOfCustodyService {

    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final EvidenceItemRepository evidenceItemRepository;

    /*
      Retrieves the complete, immutable chronological audit ledger for an evidence item.
     */
    @Transactional(readOnly = true)
    public List<ChainOfCustodyResponse> getAuditTrailForEvidence(Long evidenceId) {
        if (!evidenceItemRepository.existsById(evidenceId)) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId);
        }

        return custodyLogRepository.findByEvidenceItemIdOrderByLoggedAtAsc(evidenceId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ChainOfCustodyResponse mapToResponse(ChainOfCustodyLog log) {
        return ChainOfCustodyResponse.builder()
                .id(log.getId())
                .evidenceId(log.getEvidenceItem().getId())
                .evidenceTrackingCode(log.getEvidenceItem().getItemTrackingCode())
                .evidenceName(log.getEvidenceItem().getName())
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorName(log.getActor() != null ? log.getActor().getFullName() : null)
                .actorEmail(log.getActor() != null ? log.getActor().getEmail() : null)
                .actionType(log.getActionType())
                .notes(log.getNotes())
                .hashAtEvent(log.getHashAtEvent())
                .loggedAt(log.getLoggedAt())
                .build();
    }
}