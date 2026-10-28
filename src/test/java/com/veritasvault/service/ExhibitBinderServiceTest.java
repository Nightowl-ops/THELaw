package com.veritasvault.service;

import com.veritasvault.dto.request.AddExhibitRequest;
import com.veritasvault.dto.request.CreateBinderRequest;
import com.veritasvault.dto.response.BinderResponse;
import com.veritasvault.exception.BadRequestException;
import com.veritasvault.exception.ConflictException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExhibitBinderServiceTest {

    @Mock
    private ExhibitBinderRepository binderRepository;
    @Mock
    private BinderExhibitRepository binderExhibitRepository;
    @Mock
    private LegalCaseRepository legalCaseRepository;
    @Mock
    private EvidenceItemRepository evidenceItemRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExhibitBinderService binderService;

    private User attorney;
    private LegalCase legalCase;
    private ExhibitBinder binder;
    private EvidenceItem evidenceItem;

    @BeforeEach
    void setUp() {
        attorney = User.builder()
                .id(1L)
                .fullName("Rachel Zane")
                .role(Role.ROLE_ATTORNEY)
                .build();

        legalCase = LegalCase.builder()
                .id(10L)
                .caseNumber("CV-2026-9901")
                .caseTitle("State v. Sterling")
                .build();

        binder = ExhibitBinder.builder()
                .id(100L)
                .legalCase(legalCase)
                .binderName("Plaintiff Trial Exhibits")
                .batesPrefix("PLTF")
                .status(BinderStatus.DRAFT)
                .createdBy(attorney)
                .build();

        evidenceItem = EvidenceItem.builder()
                .id(50L)
                .legalCase(legalCase)
                .itemTrackingCode("EV-2026-50")
                .name("Forensic Memo")
                .sha256Hash("hash-abc-123")
                .build();
    }

    @Test
    @DisplayName("Should successfully create exhibit binder in DRAFT status")
    void createBinder_Success() {
        CreateBinderRequest request = CreateBinderRequest.builder()
                .caseId(10L)
                .name("Plaintiff Trial Exhibits")
                .batesPrefix("PLTF")
                .build();

        when(legalCaseRepository.findById(10L)).thenReturn(Optional.of(legalCase));
        when(userRepository.findById(1L)).thenReturn(Optional.of(attorney));
        when(binderRepository.existsByLegalCaseIdAndBinderName(10L, "Plaintiff Trial Exhibits")).thenReturn(false);
        when(binderRepository.save(any(ExhibitBinder.class))).thenReturn(binder);

        BinderResponse response = binderService.createBinder(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getIsLocked()).isFalse();
        assertThat(response.getBatesPrefix()).isEqualTo("PLTF");
        verify(binderRepository, times(1)).save(any(ExhibitBinder.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when creating binder with duplicate name in same case")
    void createBinder_DuplicateName_ThrowsConflictException() {
        CreateBinderRequest request = CreateBinderRequest.builder()
                .caseId(10L)
                .name("Plaintiff Trial Exhibits")
                .batesPrefix("PLTF")
                .build();

        when(legalCaseRepository.findById(10L)).thenReturn(Optional.of(legalCase));
        when(userRepository.findById(1L)).thenReturn(Optional.of(attorney));
        when(binderRepository.existsByLegalCaseIdAndBinderName(10L, "Plaintiff Trial Exhibits")).thenReturn(true);

        assertThatThrownBy(() -> binderService.createBinder(request, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(binderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject binder creation by unauthorized roles (e.g. ROLE_CLIENT)")
    void createBinder_UnauthorizedRole_ThrowsBadRequestException() {
        User clientUser = User.builder()
                .id(2L)
                .role(Role.ROLE_CLIENT)
                .build();

        CreateBinderRequest request = CreateBinderRequest.builder()
                .caseId(10L)
                .name("Unauthorized Binder")
                .batesPrefix("UNAUTH")
                .build();

        when(legalCaseRepository.findById(10L)).thenReturn(Optional.of(legalCase));
        when(userRepository.findById(2L)).thenReturn(Optional.of(clientUser));

        assertThatThrownBy(() -> binderService.createBinder(request, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only attorneys or administrators");
    }

    @Test
    @DisplayName("Should add exhibit to binder and calculate Bates range correctly")
    void addExhibitToBinder_Success() {
        AddExhibitRequest request = AddExhibitRequest.builder()
                .evidenceId(50L)
                .exhibitLabel("EXHIBIT-1")
                .pageCount(5)
                .build();

        when(binderRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(binder));
        when(evidenceItemRepository.findById(50L)).thenReturn(Optional.of(evidenceItem));
        when(binderExhibitRepository.existsByBinderIdAndEvidenceItemId(100L, 50L)).thenReturn(false);

        BinderExhibit createdExhibit = BinderExhibit.builder()
                .id(1L)
                .binder(binder)
                .evidenceItem(evidenceItem)
                .exhibitNumber("EXHIBIT-1")
                .batesStartNum(1)
                .batesEndNum(5)
                .build();

        when(binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(100L))
                .thenReturn(List.of())
                .thenReturn(List.of(createdExhibit));

        BinderResponse response = binderService.addExhibitToBinder(100L, request);

        assertThat(response).isNotNull();
        assertThat(response.getExhibits()).hasSize(1);
        assertThat(response.getExhibits().get(0).getBatesStart()).isEqualTo("PLTF-0001");
        assertThat(response.getExhibits().get(0).getBatesEnd()).isEqualTo("PLTF-0005");
        verify(binderExhibitRepository, times(1)).save(any(BinderExhibit.class));
    }

    @Test
    @DisplayName("Should reject adding exhibit when binder is FINALIZED (immutability lock)")
    void addExhibitToBinder_FinalizedBinder_ThrowsBadRequestException() {
        binder.setStatus(BinderStatus.FINALIZED);

        AddExhibitRequest request = AddExhibitRequest.builder()
                .evidenceId(50L)
                .exhibitLabel("EXHIBIT-LOCKED")
                .pageCount(2)
                .build();

        when(binderRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(binder));

        assertThatThrownBy(() -> binderService.addExhibitToBinder(100L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be modified");

        verify(binderExhibitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when adding duplicate evidence item to binder")
    void addExhibitToBinder_DuplicateEvidence_ThrowsConflictException() {
        AddExhibitRequest request = AddExhibitRequest.builder()
                .evidenceId(50L)
                .exhibitLabel("EXHIBIT-1")
                .pageCount(3)
                .build();

        when(binderRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(binder));
        when(evidenceItemRepository.findById(50L)).thenReturn(Optional.of(evidenceItem));
        when(binderExhibitRepository.existsByBinderIdAndEvidenceItemId(100L, 50L)).thenReturn(true);

        assertThatThrownBy(() -> binderService.addExhibitToBinder(100L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already included");

        verify(binderExhibitRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should remove exhibit and recalculate consecutive Bates numbers for remaining exhibits")
    void removeExhibitFromBinder_Success() {
        BinderExhibit exhibit1 = BinderExhibit.builder()
                .id(1L)
                .binder(binder)
                .evidenceItem(evidenceItem)
                .exhibitNumber("EX-A")
                .batesStartNum(1)
                .batesEndNum(4)
                .build();

        EvidenceItem evidenceItem2 = EvidenceItem.builder()
                .id(51L)
                .legalCase(legalCase)
                .itemTrackingCode("EV-2026-51")
                .name("Second Item")
                .build();

        BinderExhibit exhibit2 = BinderExhibit.builder()
                .id(2L)
                .binder(binder)
                .evidenceItem(evidenceItem2)
                .exhibitNumber("EX-B")
                .batesStartNum(5)
                .batesEndNum(10)
                .build();

        when(binderRepository.findByIdWithPessimisticLock(100L)).thenReturn(Optional.of(binder));
        when(binderExhibitRepository.findById(1L)).thenReturn(Optional.of(exhibit1));
        List<BinderExhibit> remaining = new ArrayList<>(List.of(exhibit2));
        when(binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(100L)).thenReturn(remaining);

        BinderResponse response = binderService.removeExhibitFromBinder(100L, 1L);

        assertThat(response).isNotNull();
        // After removing exhibit1 (pages 1-4), exhibit2 (6 pages) should be re-indexed to start at 1 and end at 6
        assertThat(exhibit2.getBatesStartNum()).isEqualTo(1);
        assertThat(exhibit2.getBatesEndNum()).isEqualTo(6);
        verify(binderExhibitRepository, times(1)).delete(exhibit1);
        verify(binderExhibitRepository, times(1)).saveAll(remaining);
    }

    @Test
    @DisplayName("Should prevent reverting a FINALIZED binder to DRAFT (immutability lock)")
    void updateStatus_FinalizedCannotRevertToDraft_ThrowsBadRequestException() {
        binder.setStatus(BinderStatus.FINALIZED);

        when(binderRepository.findById(100L)).thenReturn(Optional.of(binder));

        assertThatThrownBy(() -> binderService.updateStatus(100L, BinderStatus.DRAFT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot revert a FINALIZED binder");

        verify(binderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should prevent modifying status of a SERVED binder")
    void updateStatus_ServedCannotModify_ThrowsBadRequestException() {
        binder.setStatus(BinderStatus.SERVED);

        when(binderRepository.findById(100L)).thenReturn(Optional.of(binder));

        assertThatThrownBy(() -> binderService.updateStatus(100L, BinderStatus.DRAFT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot modify status of a SERVED binder");

        verify(binderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow transitioning FINALIZED binder to SERVED")
    void updateStatus_FinalizedToServed_Success() {
        binder.setStatus(BinderStatus.FINALIZED);

        when(binderRepository.findById(100L)).thenReturn(Optional.of(binder));
        when(binderRepository.save(any(ExhibitBinder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(100L)).thenReturn(List.of());

        BinderResponse response = binderService.updateStatus(100L, BinderStatus.SERVED);

        assertThat(response.getIsLocked()).isTrue();
        verify(binderRepository, times(1)).save(binder);
    }
}
