package com.veritasvault.service;

import com.veritasvault.dto.request.AddExhibitRequest;
import com.veritasvault.dto.request.CreateBinderRequest;
import com.veritasvault.dto.response.BinderResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ResourceNotFoundException;
import com.veritasvault.model.BinderExhibit;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.ExhibitBinder;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.BinderStatus;
import com.veritasvault.model.enums.Role;
import com.veritasvault.repository.BinderExhibitRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.ExhibitBinderRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing exhibit binder creation, trial bundle preparation,
 * immutability enforcement, and consecutive Bates stamping calculations.
 */
@Service
@RequiredArgsConstructor
public class ExhibitBinderService {

    private final ExhibitBinderRepository binderRepository;
    private final BinderExhibitRepository binderExhibitRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final UserRepository userRepository;

    /**
     * Initializes a new trial exhibit binder in DRAFT status.
     */
    @Transactional
    public BinderResponse createBinder(CreateBinderRequest request, Long currentUserId) {
        LegalCase legalCase = legalCaseRepository.findById(request.getCaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Legal case not found with ID: " + request.getCaseId()));

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        if (creator.getRole() != Role.ROLE_ATTORNEY && creator.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException("Only attorneys or administrators can create exhibit binders");
        }

        ExhibitBinder binder = ExhibitBinder.builder()
                .legalCase(legalCase)
                .binderName(request.getName().trim())
                .batesPrefix(request.getBatesPrefix().toUpperCase().trim())
                .status(BinderStatus.DRAFT)
                .createdBy(creator)
                .build();

        ExhibitBinder savedBinder = binderRepository.save(binder);
        return mapToResponse(savedBinder, List.of());
    }

    /**
     * Attaches an evidence item to a binder with sequential Bates numbering.
     * Enforces immutability: additions are blocked if binder is FINALIZED or SERVED.
     */
    @Transactional
    public BinderResponse addExhibitToBinder(Long binderId, AddExhibitRequest request) {
        ExhibitBinder binder = findBinderById(binderId);

        // Immutability Rule #3: No additions to locked binders
        assertBinderIsMutable(binder);

        EvidenceItem evidence = evidenceItemRepository.findById(request.getEvidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Evidence item not found with ID: " + request.getEvidenceId()));

        // Ensure evidence belongs to the same legal case
        if (!evidence.getLegalCase().getId().equals(binder.getLegalCase().getId())) {
            throw new BadRequestException("Evidence item does not belong to case #" + binder.getLegalCase().getCaseNumber());
        }

        // Avoid adding the same evidence item twice to the same binder
        if (binderExhibitRepository.existsByBinderIdAndEvidenceItemId(binderId, evidence.getId())) {
            throw new BadRequestException("Evidence item is already included in this binder");
        }

        // Calculate consecutive Bates page offset
        List<BinderExhibit> existingExhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(binderId);
        int nextBatesStart = 1;
        if (!existingExhibits.isEmpty()) {
            BinderExhibit lastExhibit = existingExhibits.get(existingExhibits.size() - 1);
            nextBatesStart = lastExhibit.getBatesEndNum() + 1;
        }

        int batesEnd = nextBatesStart + request.getPageCount() - 1;

        BinderExhibit binderExhibit = BinderExhibit.builder()
                .binder(binder)
                .evidenceItem(evidence)
                .exhibitNumber(request.getExhibitLabel().trim())
                .batesStartNum(nextBatesStart)
                .batesEndNum(batesEnd)
                .build();

        binderExhibitRepository.save(binderExhibit);

        List<BinderExhibit> updatedExhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(binderId);
        return mapToResponse(binder, updatedExhibits);
    }

    /**
     * Locks a binder by transitioning status to FINALIZED or SERVED.
     */
    @Transactional
    public BinderResponse updateStatus(Long binderId, BinderStatus newStatus) {
        ExhibitBinder binder = findBinderById(binderId);

        if (binder.getStatus() == BinderStatus.SERVED && newStatus != BinderStatus.SERVED) {
            throw new BadRequestException("Cannot modify status of a SERVED binder");
        }

        binder.setStatus(newStatus);
        ExhibitBinder updated = binderRepository.save(binder);

        List<BinderExhibit> exhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(binderId);
        return mapToResponse(updated, exhibits);
    }

    @Transactional(readOnly = true)
    public BinderResponse getBinderById(Long id) {
        ExhibitBinder binder = findBinderById(id);
        List<BinderExhibit> exhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(id);
        return mapToResponse(binder, exhibits);
    }

    @Transactional(readOnly = true)
    public List<BinderResponse> getBindersByCaseId(Long caseId) {
        if (!legalCaseRepository.existsById(caseId)) {
            throw new ResourceNotFoundException("Legal case not found with ID: " + caseId);
        }
        return binderRepository.findByLegalCaseId(caseId)
                .stream()
                .map(binder -> {
                    List<BinderExhibit> exhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(binder.getId());
                    return mapToResponse(binder, exhibits);
                })
                .collect(Collectors.toList());
    }

    // --- Helper Validation & Mapping Methods ---

    private ExhibitBinder findBinderById(Long id) {
        return binderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exhibit binder not found with ID: " + id));
    }

    private void assertBinderIsMutable(ExhibitBinder binder) {
        if (binder.getStatus() == BinderStatus.FINALIZED || binder.getStatus() == BinderStatus.SERVED) {
            throw new BadRequestException("Binder '" + binder.getBinderName() + "' is " + binder.getStatus() + " and cannot be modified");
        }
    }

    private BinderResponse mapToResponse(ExhibitBinder binder, List<BinderExhibit> exhibits) {
        int totalPages = exhibits.isEmpty() ? 0 : exhibits.get(exhibits.size() - 1).getBatesEndNum();
        boolean isLocked = binder.getStatus() == BinderStatus.FINALIZED || binder.getStatus() == BinderStatus.SERVED;

        List<BinderResponse.ExhibitItemResponse> exhibitResponses = exhibits.stream()
                .map(e -> BinderResponse.ExhibitItemResponse.builder()
                        .id(e.getId())
                        .evidenceId(e.getEvidenceItem().getId())
                        .evidenceTrackingCode(e.getEvidenceItem().getItemTrackingCode())
                        .evidenceName(e.getEvidenceItem().getName())
                        .exhibitLabel(e.getExhibitNumber())
                        .pageCount((e.getBatesEndNum() - e.getBatesStartNum()) + 1)
                        .batesStart(String.format("%s-%04d", binder.getBatesPrefix(), e.getBatesStartNum()))
                        .batesEnd(String.format("%s-%04d", binder.getBatesPrefix(), e.getBatesEndNum()))
                        .build())
                .collect(Collectors.toList());

        return BinderResponse.builder()
                .id(binder.getId())
                .caseId(binder.getLegalCase().getId())
                .caseNumber(binder.getLegalCase().getCaseNumber())
                .name(binder.getBinderName())
                .batesPrefix(binder.getBatesPrefix())
                .totalExhibits(exhibits.size())
                .totalPages(totalPages)
                .isLocked(isLocked)
                .createdById(binder.getCreatedBy().getId())
                .createdByName(binder.getCreatedBy().getFullName())
                .exhibits(exhibitResponses)
                .createdAt(binder.getCreatedAt())
                .updatedAt(binder.getUpdatedAt())
                .build();
    }
}