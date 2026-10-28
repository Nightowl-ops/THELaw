package com.veritasvault.service;

import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import com.veritasvault.model.CustodyReservation;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.CustodyActionType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.ReservationStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.CustodyReservationRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.ForensicLabReportRepository;
import com.veritasvault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustodyReservationServiceTest {

    @Mock
    private CustodyReservationRepository reservationRepository;
    @Mock
    private EvidenceItemRepository evidenceItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChainOfCustodyLogRepository custodyLogRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ForensicLabReportRepository labReportRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private com.veritasvault.repository.LegalCaseRepository legalCaseRepository;

    @InjectMocks
    private CustodyReservationService reservationService;

    private User examiner;
    private EvidenceItem evidence;
    private LocalDateTime futureStart;
    private LocalDateTime futureEnd;

    @BeforeEach
    void setUp() {
        examiner = User.builder()
                .id(10L)
                .fullName("Marcus Vance")
                .role(Role.ROLE_FORENSIC_EXAMINER)
                .build();

        evidence = EvidenceItem.builder()
                .id(100L)
                .itemTrackingCode("EV-2026-0001")
                .name("Hard Drive Dump")
                .status(EvidenceStatus.AVAILABLE)
                .sha256Hash("dummy-hash-123")
                .build();

        futureStart = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        futureEnd = futureStart.plusHours(2);
    }

    @Test
    @DisplayName("Should successfully create reservation and broadcast SSE when time slot is available")
    void createReservation_Success() {
        CustodyReservationRequest request = CustodyReservationRequest.builder()
                .evidenceItemId(100L)
                .startTime(futureStart)
                .endTime(futureEnd)
                .notes("Standard analysis")
                .build();

        when(evidenceItemRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(evidence));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.findOverlappingReservations(eq(100L), eq(futureStart), eq(futureEnd), any()))
                .thenReturn(Collections.emptyList());

        CustodyReservation savedEntity = CustodyReservation.builder()
                .id(501L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .startTime(futureStart)
                .endTime(futureEnd)
                .status(ReservationStatus.CONFIRMED)
                .notes("Standard analysis")
                .build();

        when(reservationRepository.save(any(CustodyReservation.class))).thenReturn(savedEntity);

        CustodyReservationResponse response = reservationService.createReservation(request, 10L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.RESERVED);

        verify(notificationService, times(1)).broadcast(eq("CUSTODY_RESERVATION_CREATED"), any());
        verify(evidenceItemRepository, times(1)).save(evidence);
    }

    @Test
    @DisplayName("Should throw ConflictException (409) when booking conflicts with an existing active reservation")
    void createReservation_CollisionDetected_ThrowsException() {
        CustodyReservationRequest request = CustodyReservationRequest.builder()
                .evidenceItemId(100L)
                .startTime(futureStart)
                .endTime(futureEnd)
                .build();

        when(evidenceItemRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(evidence));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));

        CustodyReservation conflicting = CustodyReservation.builder()
                .id(999L)
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findOverlappingReservations(eq(100L), eq(futureStart), eq(futureEnd), any()))
                .thenReturn(List.of(conflicting));

        assertThatThrownBy(() -> reservationService.createReservation(request, 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Time conflict");

        verify(reservationRepository, never()).save(any());
        verify(notificationService, never()).broadcast(any(), any());
    }

    @Test
    @DisplayName("Should reject booking if evidence item is marked SEQUESTERED")
    void createReservation_SequesteredEvidence_ThrowsException() {
        evidence.setStatus(EvidenceStatus.SEQUESTERED);
        CustodyReservationRequest request = CustodyReservationRequest.builder()
                .evidenceItemId(100L)
                .startTime(futureStart)
                .endTime(futureEnd)
                .build();

        when(evidenceItemRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> reservationService.createReservation(request, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SEQUESTERED");
    }

    @Test
    @DisplayName("Should checkout evidence: update reservation to ACTIVE, evidence to IN_LAB, and log custody")
    void checkOutEvidence_Success() {
        CustodyReservation reservation = CustodyReservation.builder()
                .id(200L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(200L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CustodyReservationResponse response = reservationService.checkOutEvidence(200L, 10L);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.IN_LAB);

        verify(custodyLogRepository, times(1)).save(argThat(log ->
                log.getActionType() == CustodyActionType.CHECK_OUT &&
                        log.getEvidenceItem().getId().equals(100L)
        ));
        verify(notificationService, times(1)).broadcast(eq("EVIDENCE_CHECKED_OUT"), any());
    }

    @Test
    @DisplayName("Should deny checkout when examiner does not own the reservation")
    void checkOutEvidence_UnassignedExaminer_ThrowsAccessDenied() {
        User anotherExaminer = User.builder()
                .id(20L)
                .fullName("Dr. Jekyll")
                .role(Role.ROLE_FORENSIC_EXAMINER)
                .build();

        CustodyReservation reservation = CustodyReservation.builder()
                .id(200L)
                .evidenceItem(evidence)
                .examiner(examiner) // Owned by ID 10L
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(200L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(20L)).thenReturn(Optional.of(anotherExaminer));

        assertThatThrownBy(() -> reservationService.checkOutEvidence(200L, 20L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");

        verify(custodyLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should cancel reservation, release evidence back to AVAILABLE, and log custody event")
    void cancelReservation_Success() {
        evidence.setStatus(EvidenceStatus.RESERVED);
        CustodyReservation reservation = CustodyReservation.builder()
                .id(300L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(300L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CustodyReservationResponse response = reservationService.cancelReservation(300L, 10L);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.AVAILABLE);

        verify(evidenceItemRepository, times(1)).save(evidence);
        verify(custodyLogRepository, times(1)).save(argThat(log ->
                log.getActionType() == CustodyActionType.CHECK_IN &&
                        log.getNotes().contains("cancelled")
        ));
        verify(notificationService, times(1)).broadcast(eq("CUSTODY_RESERVATION_CANCELLED"), any());
    }

    @Test
    @DisplayName("Should deny cancellation when examiner does not own the reservation")
    void cancelReservation_UnassignedExaminer_ThrowsAccessDenied() {
        User unauthorizedUser = User.builder()
                .id(99L)
                .fullName("Unauthorized Attorney")
                .role(Role.ROLE_ATTORNEY)
                .build();

        CustodyReservation reservation = CustodyReservation.builder()
                .id(300L)
                .evidenceItem(evidence)
                .examiner(examiner) // ID 10L
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(300L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(99L)).thenReturn(Optional.of(unauthorizedUser));

        assertThatThrownBy(() -> reservationService.cancelReservation(300L, 99L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should check in evidence: mark reservation COMPLETED, evidence AVAILABLE, and log check-in")
    void checkInEvidence_Success() throws Exception {
        byte[] testBytes = "clean checkin data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String testHash = com.veritasvault.util.ChecksumUtils.calculateSha256(new java.io.ByteArrayInputStream(testBytes));
        evidence.setSha256Hash(testHash);
        evidence.setStorageUrl("evidence/case-1/test.bin");
        evidence.setStatus(EvidenceStatus.IN_LAB);

        org.springframework.core.io.Resource mockResource = mock(org.springframework.core.io.Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.isReadable()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(testBytes));
        when(fileStorageService.loadAsResource("evidence/case-1/test.bin")).thenReturn(mockResource);

        CustodyReservation reservation = CustodyReservation.builder()
                .id(400L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .status(ReservationStatus.ACTIVE)
                .build();

        when(reservationRepository.findById(400L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CustodyReservationResponse response = reservationService.checkInEvidence(400L, "Analysis completed cleanly", 10L);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.AVAILABLE);

        verify(evidenceItemRepository, times(1)).save(evidence);
        verify(custodyLogRepository, times(1)).save(argThat(log ->
                log.getActionType() == CustodyActionType.CHECK_IN &&
                        log.getEvidenceItem().getId().equals(100L)
        ));
        verify(notificationService, times(1)).broadcast(eq("EVIDENCE_CHECKED_IN"), any());
    }

    @Test
    @DisplayName("Should generate ForensicLabReport with verified status and multithreading telemetry on check-in")
    void checkInWithLabReport_Success() throws Exception {
        byte[] testBytes = "clean forensic data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String testHash = com.veritasvault.util.ChecksumUtils.calculateSha256(new java.io.ByteArrayInputStream(testBytes));
        evidence.setSha256Hash(testHash);
        evidence.setStorageUrl("evidence/case-1/test.bin");
        evidence.setStatus(EvidenceStatus.IN_LAB);

        org.springframework.core.io.Resource mockResource = mock(org.springframework.core.io.Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.isReadable()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(testBytes));
        when(fileStorageService.loadAsResource("evidence/case-1/test.bin")).thenReturn(mockResource);

        CustodyReservation reservation = CustodyReservation.builder()
                .id(450L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .status(ReservationStatus.ACTIVE)
                .build();

        when(reservationRepository.findById(450L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        com.veritasvault.dto.request.CreateLabReportRequest request = com.veritasvault.dto.request.CreateLabReportRequest.builder()
                .examinerFindings("Microfilm analysis completed without tampering")
                .notes("All frames intact")
                .build();

        com.veritasvault.dto.response.ForensicLabReportResponse reportResponse =
                reservationService.checkInWithLabReport(450L, request, 10L);

        assertThat(reportResponse).isNotNull();
        assertThat(reportResponse.getIntegrityStatus()).isEqualTo(com.veritasvault.model.enums.LabIntegrityStatus.VERIFIED);
        assertThat(reportResponse.getVerifiedByThread()).startsWith("lab-verifier-pool-");
        assertThat(reportResponse.getExaminerFindings()).contains("Microfilm analysis");
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Should mark integrity COMPROMISED and quarantine evidence as SEQUESTERED when evidence file is missing from disk")
    void checkInWithLabReport_MissingFile_MarksCompromised() {
        evidence.setStatus(EvidenceStatus.IN_LAB);
        evidence.setStorageUrl("evidence/case-1/missing.bin");
        evidence.setSha256Hash("original-hash-123");

        CustodyReservation reservation = CustodyReservation.builder()
                .id(460L)
                .evidenceItem(evidence)
                .examiner(examiner)
                .status(ReservationStatus.ACTIVE)
                .build();

        when(reservationRepository.findById(460L)).thenReturn(Optional.of(reservation));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(labReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageService.loadAsResource("evidence/case-1/missing.bin"))
                .thenThrow(new com.veritasvault.exception.ResourceNotFoundException("File not found on disk"));

        com.veritasvault.dto.request.CreateLabReportRequest request = com.veritasvault.dto.request.CreateLabReportRequest.builder()
                .examinerFindings("Attempted check-in but file is missing")
                .notes("Missing file test")
                .build();

        com.veritasvault.dto.response.ForensicLabReportResponse reportResponse =
                reservationService.checkInWithLabReport(460L, request, 10L);

        assertThat(reportResponse).isNotNull();
        assertThat(reportResponse.getIntegrityStatus()).isEqualTo(com.veritasvault.model.enums.LabIntegrityStatus.COMPROMISED);
        assertThat(reportResponse.getPostExamHash()).contains("FILE_READ_ERROR");
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.SEQUESTERED);

        verify(notificationService, times(1)).broadcast(eq("EVIDENCE_COMPROMISED"), any());
        verify(evidenceItemRepository, times(1)).save(evidence);
    }

    @Test
    @DisplayName("Should execute parallel case integrity audit across thread pool using invokeAll()")
    void auditCaseEvidenceIntegrity_ParallelInvokeAll_Success() {
        com.veritasvault.model.LegalCase legalCase = com.veritasvault.model.LegalCase.builder()
                .id(1L)
                .caseNumber("CV-2026-0001")
                .caseTitle("State v. John Doe")
                .build();

        EvidenceItem item1 = EvidenceItem.builder()
                .id(101L)
                .itemTrackingCode("EV-2026-0001")
                .name("Evidence 1")
                .sha256Hash("hash-1")
                .storageUrl("evidence/case-1/ev1.bin")
                .status(EvidenceStatus.AVAILABLE)
                .build();

        EvidenceItem item2 = EvidenceItem.builder()
                .id(102L)
                .itemTrackingCode("EV-2026-0002")
                .name("Evidence 2")
                .sha256Hash("hash-2")
                .storageUrl("evidence/case-1/ev2.bin")
                .status(EvidenceStatus.AVAILABLE)
                .build();

        when(legalCaseRepository.findById(1L)).thenReturn(Optional.of(legalCase));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));
        when(evidenceItemRepository.findByLegalCaseId(1L)).thenReturn(List.of(item1, item2));

        com.veritasvault.dto.response.CaseAuditResponse auditResponse =
                reservationService.auditCaseEvidenceIntegrity(1L, 10L);

        assertThat(auditResponse).isNotNull();
        assertThat(auditResponse.getCaseId()).isEqualTo(1L);
        assertThat(auditResponse.getTotalEvidenceAudited()).isEqualTo(2);
        assertThat(auditResponse.getAuditItems()).hasSize(2);
        assertThat(auditResponse.getAuditItems().get(0).getVerifiedByThread()).startsWith("lab-verifier-pool-");
        assertThat(auditResponse.getAuditItems().get(1).getVerifiedByThread()).startsWith("lab-verifier-pool-");

        verify(notificationService, times(1)).broadcast(any(), any());
    }
}