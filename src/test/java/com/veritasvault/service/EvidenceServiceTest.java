package com.veritasvault.service;

import com.veritasvault.dto.request.EvidenceUploadRequest;
import com.veritasvault.dto.response.EvidenceResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.model.ChainOfCustodyLog;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CustodyActionType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock
    private EvidenceItemRepository evidenceItemRepository;
    @Mock
    private LegalCaseRepository legalCaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChainOfCustodyLogRepository custodyLogRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private EvidenceService evidenceService;

    private LegalCase testCase;
    private User testUser;

    @BeforeEach
    void setUp() {
        testCase = LegalCase.builder().id(1L).caseNumber("CR-2026-0041").build();
        testUser = User.builder().id(2L).fullName("Sarah Jenkins").role(Role.ROLE_ATTORNEY).build();
    }

    @Test
    @DisplayName("Should calculate SHA-256, store file, create tracking code and INGEST custody log")
    void uploadEvidence_Success() {
        byte[] content = "Hello Forensic World".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "extraction.bin", "application/octet-stream", content
        );

        EvidenceUploadRequest request = EvidenceUploadRequest.builder()
                .caseId(1L)
                .name("Extraction Bin")
                .evidenceType(EvidenceType.DEVICE_IMAGE)
                .sourceOrigin("Crime Scene Locker")
                .build();

        when(legalCaseRepository.findById(1L)).thenReturn(Optional.of(testCase));
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser));
        when(fileStorageService.storeFile(any(), any())).thenReturn("evidence/case-1/sample.bin");
        when(evidenceItemRepository.count()).thenReturn(5L);

        when(evidenceItemRepository.save(any(EvidenceItem.class))).thenAnswer(invocation -> {
            EvidenceItem item = invocation.getArgument(0);
            item.setId(50L);
            return item;
        });

        EvidenceResponse response = evidenceService.uploadEvidence(file, request, 2L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getSha256Checksum()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(EvidenceStatus.INGESTED);

        // Verify immutable INGEST entry creation
        verify(custodyLogRepository, times(1)).save(argThat(log ->
                log.getActionType() == CustodyActionType.INGEST &&
                        log.getHashAtEvent().equals(response.getSha256Checksum())
        ));
    }

    @Test
    @DisplayName("Should reject empty multipart upload")
    void uploadEvidence_EmptyFile_ThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        EvidenceUploadRequest request = EvidenceUploadRequest.builder().caseId(1L).build();

        assertThatThrownBy(() -> evidenceService.uploadEvidence(emptyFile, request, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }
}