package com.veritasvault.forensicvault.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veritasvault.forensicvault.exception.ResourceNotFoundException;
import com.veritasvault.forensicvault.model.enums.EvidenceStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CaseServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CaseServiceClient(
            @Value("${services.case.url:http://localhost:8082}") String caseServiceUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(caseServiceUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public EvidenceItemDto getEvidenceById(Long evidenceId, String jwt) {
        if (evidenceId == null) {
            return null;
        }
        try {
            var request = restClient.get().uri("/api/evidence/{evidenceId}", evidenceId);
            if (jwt != null && !jwt.isBlank()) {
                request.header("Authorization", jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt);
            }
            return request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId + " in Case Service");
                    })
                    .body(EvidenceItemDto.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Case Service unreachable or failed for evidence ID {}: {}", evidenceId, e.getMessage());
            return null;
        }
    }

    public List<EvidenceItemDto> getEvidenceByCaseId(Long caseId, String jwt) {
        List<EvidenceItemDto> results = new ArrayList<>();
        if (caseId == null) {
            return results;
        }
        try {
            var request = restClient.get().uri("/api/evidence/case/{caseId}?size=1000", caseId);
            if (jwt != null && !jwt.isBlank()) {
                request.header("Authorization", jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt);
            }
            String rawJson = request.retrieve().body(String.class);
            if (rawJson != null) {
                JsonNode root = objectMapper.readTree(rawJson);
                JsonNode content = root.has("content") ? root.get("content") : root;
                if (content.isArray()) {
                    for (JsonNode node : content) {
                        results.add(objectMapper.treeToValue(node, EvidenceItemDto.class));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch evidence items for case ID {}: {}", caseId, e.getMessage());
        }
        return results;
    }

    public LegalCaseDto getCaseById(Long caseId, String jwt) {
        if (caseId == null) {
            return null;
        }
        try {
            var request = restClient.get().uri("/api/cases/{caseId}", caseId);
            if (jwt != null && !jwt.isBlank()) {
                request.header("Authorization", jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt);
            }
            return request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("Legal case not found with ID: " + caseId + " in Case Service");
                    })
                    .body(LegalCaseDto.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch legal case ID {}: {}", caseId, e.getMessage());
            return null;
        }
    }

    public void updateEvidenceStatus(Long evidenceId, EvidenceStatus status, String notes, String jwt) {
        if (evidenceId == null || status == null) {
            return;
        }
        try {
            var request = restClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/evidence/{id}/status")
                            .queryParam("status", status.name())
                            .queryParam("notes", notes != null ? notes : "")
                            .build(evidenceId));
            if (jwt != null && !jwt.isBlank()) {
                request.header("Authorization", jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt);
            }
            request.retrieve().toBodilessEntity();
            log.info("Successfully requested status update for evidence #{} -> {}", evidenceId, status);
        } catch (Exception e) {
            log.warn("Could not synchronize evidence status with Case Service for ID {}: {}", evidenceId, e.getMessage());
        }
    }
}
