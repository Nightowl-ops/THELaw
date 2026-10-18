package com.veritasvault.service;

import com.veritasvault.dto.request.CustodyReservationRequest;
import com.veritasvault.dto.response.CustodyReservationResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ConflictException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustodyReservationService {

    private final CustodyReservationRepository reservationRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final UserRepository userRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final NotificationService notificationService;

    @Transactional
    public CustodyReservationResponse createReservation(CustodyReservationRequest request, Long examinerId) {

        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().isEqual(request.getEndTime())) {
            throw new BadRequestException("Reservation start time must be strictly before end time");
        }

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Cannot create a reservation for a date or time in the past");
        }

        EvidenceItem evidence = evidenceItemRepository.findByIdWithPessimisticLock(request.getEvidenceItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Evidence item not found with ID: " + request.getEvidenceItemId()));

        if (evidence.getStatus() == EvidenceStatus.SEQUESTERED || evidence.getStatus() == EvidenceStatus.ADMITTED) {
            throw new BadRequestException("Evidence item cannot be reserved: current status is " + evidence.getStatus());
        }

        User examiner = userRepository.findById(examinerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + examinerId));

        if (examiner.getRole() != Role.ROLE_FORENSIC_EXAMINER && examiner.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException("Only users with ROLE_FORENSIC_EXAMINER or ROLE_ADMIN can reserve custody slots");
        }

        List<ReservationStatus> blockingStatuses = List.of(ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE);
        List<CustodyReservation> overlaps = reservationRepository.findOverlappingReservations(
                evidence.getId(),
                request.getStartTime(),
                request.getEndTime(),
                blockingStatuses
        );

        if (!overlaps.isEmpty()) {
            throw new ConflictException("Time conflict: Evidence item is already booked during the requested interval");
        }

        CustodyReservation reservation = CustodyReservation.builder()
                .evidenceItem(evidence)
                .examiner(examiner)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .notes(request.getNotes())
                .build();

        CustodyReservation savedReservation = reservationRepository.save(reservation);

        evidence.setStatus(EvidenceStatus.RESERVED);
        evidenceItemRepository.save(evidence);

        CustodyReservationResponse response = mapToResponse(savedReservation);

        notificationService.broadcast("CUSTODY_RESERVATION_CREATED", response);

        return response;
    }

    @Transactional
    public CustodyReservationResponse cancelReservation(Long reservationId, Long currentUserId) {
        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You do not have permission to cancel this reservation");
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed reservation");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Reservation is already cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        CustodyReservation updated = reservationRepository.save(reservation);

        EvidenceItem evidence = reservation.getEvidenceItem();
        if (evidence.getStatus() == EvidenceStatus.RESERVED) {
            evidence.setStatus(EvidenceStatus.AVAILABLE);
            evidenceItemRepository.save(evidence);
        }

        ChainOfCustodyLog cancellationLog = ChainOfCustodyLog.builder()
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Reservation #" + reservation.getId() + " cancelled. Evidence slot released.")
                .hashAtEvent(evidence.getSha256Hash())
                .build();
        custodyLogRepository.save(cancellationLog);

        CustodyReservationResponse response = mapToResponse(updated);
        notificationService.broadcast("CUSTODY_RESERVATION_CANCELLED", response);
        return response;
    }

    @Transactional
    public CustodyReservationResponse checkOutEvidence(Long reservationId, Long currentUserId) {
        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check out reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Only CONFIRMED reservations can be checked out");
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        CustodyReservation updated = reservationRepository.save(reservation);

        EvidenceItem evidence = reservation.getEvidenceItem();
        evidence.setStatus(EvidenceStatus.IN_LAB);
        evidenceItemRepository.save(evidence);

        ChainOfCustodyLog log = ChainOfCustodyLog.builder()
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_OUT)
                .notes("Evidence checked out for lab examination under reservation #" + reservation.getId())
                .hashAtEvent(evidence.getSha256Hash())
                .build();
        custodyLogRepository.save(log);

        CustodyReservationResponse response = mapToResponse(updated);

        notificationService.broadcast("EVIDENCE_CHECKED_OUT", response);

        return response;
    }

    @Transactional
    public CustodyReservationResponse checkInEvidence(Long reservationId, String checkInNotes, Long currentUserId) {
        CustodyReservation reservation = findReservationById(reservationId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        boolean isAssignedExaminer = reservation.getExaminer().getId().equals(currentUserId);
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isAssignedExaminer && !isAdmin) {
            throw new AccessDeniedException("Access denied: You can only check in reservations assigned to you");
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new BadRequestException("Only ACTIVE reservations can be checked in");
        }

        reservation.setStatus(ReservationStatus.COMPLETED);
        CustodyReservation updated = reservationRepository.save(reservation);

        EvidenceItem evidence = reservation.getEvidenceItem();
        evidence.setStatus(EvidenceStatus.AVAILABLE);
        evidenceItemRepository.save(evidence);

        ChainOfCustodyLog log = ChainOfCustodyLog.builder()
                .evidenceItem(evidence)
                .actor(currentUser)
                .actionType(CustodyActionType.CHECK_IN)
                .notes("Evidence checked in from lab analysis. Notes: " + (checkInNotes != null ? checkInNotes : "N/A"))
                .hashAtEvent(evidence.getSha256Hash())
                .build();
        custodyLogRepository.save(log);

        CustodyReservationResponse response = mapToResponse(updated);
        notificationService.broadcast("EVIDENCE_CHECKED_IN", response);
        return response;
    }

    @Transactional(readOnly = true)
    public CustodyReservationResponse getReservationById(Long id) {
        return mapToResponse(findReservationById(id));
    }

    @Transactional(readOnly = true)
    public List<CustodyReservationResponse> getReservationsForEvidence(Long evidenceId) {
        if (!evidenceItemRepository.existsById(evidenceId)) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId);
        }
        return reservationRepository.findByEvidenceItemId(evidenceId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForEvidence(Long evidenceId, Pageable pageable) {
        if (!evidenceItemRepository.existsById(evidenceId)) {
            throw new ResourceNotFoundException("Evidence item not found with ID: " + evidenceId);
        }
        return reservationRepository.findByEvidenceItemId(evidenceId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CustodyReservationResponse> getReservationsForExaminer(Long examinerId) {
        if (!userRepository.existsById(examinerId)) {
            throw new ResourceNotFoundException("User not found with ID: " + examinerId);
        }
        return reservationRepository.findByExaminerId(examinerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CustodyReservationResponse> getReservationsForExaminer(Long examinerId, Pageable pageable) {
        if (!userRepository.existsById(examinerId)) {
            throw new ResourceNotFoundException("User not found with ID: " + examinerId);
        }
        return reservationRepository.findByExaminerId(examinerId, pageable)
                .map(this::mapToResponse);
    }

    private CustodyReservation findReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Custody reservation not found with ID: " + id));
    }

    private CustodyReservationResponse mapToResponse(CustodyReservation reservation) {
        return CustodyReservationResponse.builder()
                .id(reservation.getId())
                .evidenceItemId(reservation.getEvidenceItem().getId())
                .evidenceTrackingCode(reservation.getEvidenceItem().getItemTrackingCode())
                .evidenceName(reservation.getEvidenceItem().getName())
                .examinerId(reservation.getExaminer().getId())
                .examinerName(reservation.getExaminer().getFullName())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(reservation.getStatus())
                .notes(reservation.getNotes())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}