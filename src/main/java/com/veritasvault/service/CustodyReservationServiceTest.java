package com.veritasvault.service;

import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.ChainOfCustodyLog;
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

        when(evidenceItemRepository.findById(100L)).thenReturn(Optional.of(evidence));
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
    @DisplayName("Should throw BadRequestException when booking conflicts with an existing active reservation")
    void createReservation_CollisionDetected_ThrowsException() {
        CustodyReservationRequest request = CustodyReservationRequest.builder()
                .evidenceItemId(100L)
                .startTime(futureStart)
                .endTime(futureEnd)
                .build();

        when(evidenceItemRepository.findById(100L)).thenReturn(Optional.of(evidence));
        when(userRepository.findById(10L)).thenReturn(Optional.of(examiner));

        CustodyReservation conflicting = CustodyReservation.builder()
                .id(999L)
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findOverlappingReservations(eq(100L), eq(futureStart), eq(futureEnd), any()))
                .thenReturn(List.of(conflicting));

        assertThatThrownBy(() -> reservationService.createReservation(request, 10L))
                .isInstanceOf(BadRequestException.class)
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

        when(evidenceItemRepository.findById(100L)).thenReturn(Optional.of(evidence));

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
}