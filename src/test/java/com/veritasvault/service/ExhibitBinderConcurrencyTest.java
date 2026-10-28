package com.veritasvault.service;

import com.veritasvault.dto.request.AddExhibitRequest;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.ExhibitBinder;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.BinderStatus;
import com.veritasvault.model.enums.CaseStatus;
import com.veritasvault.model.enums.CaseType;
import com.veritasvault.model.enums.EvidenceStatus;
import com.veritasvault.model.enums.EvidenceType;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import com.veritasvault.repository.BinderExhibitRepository;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.CustodyReservationRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.ExhibitBinderRepository;
import com.veritasvault.repository.ForensicLabReportRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class ExhibitBinderConcurrencyTest {

    @Autowired
    private ExhibitBinderService binderService;

    @Autowired
    private ExhibitBinderRepository binderRepository;

    @Autowired
    private BinderExhibitRepository binderExhibitRepository;

    @Autowired
    private ForensicLabReportRepository labReportRepository;

    @Autowired
    private ChainOfCustodyLogRepository custodyLogRepository;

    @Autowired
    private CustodyReservationRepository reservationRepository;

    @Autowired
    private EvidenceItemRepository evidenceItemRepository;

    @Autowired
    private LegalCaseRepository legalCaseRepository;

    @Autowired
    private UserRepository userRepository;

    private ExhibitBinder testBinder;
    private List<EvidenceItem> testEvidenceItems;

    @BeforeEach
    void setUp() {
        binderExhibitRepository.deleteAll();
        binderRepository.deleteAll();
        labReportRepository.deleteAll();
        custodyLogRepository.deleteAll();
        reservationRepository.deleteAll();
        evidenceItemRepository.deleteAll();
        legalCaseRepository.deleteAll();
        userRepository.deleteAll();

        User attorney = userRepository.save(User.builder()
                .fullName("Harvey Specter")
                .email("harvey.bates@lawfirm.com")
                .passwordHash("$2a$10$encodedpasswordhashhere")
                .role(Role.ROLE_ATTORNEY)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build());

        LegalCase legalCase = legalCaseRepository.save(LegalCase.builder()
                .caseNumber("CV-BATES-2026")
                .caseTitle("Pearson Hardman v. Gordon")
                .caseType(CaseType.DEFENSE)
                .status(CaseStatus.ACTIVE)
                .leadAttorney(attorney)
                .client(attorney)
                .build());

        testBinder = binderRepository.save(ExhibitBinder.builder()
                .legalCase(legalCase)
                .binderName("Trial Master Binder")
                .batesPrefix("PH")
                .status(BinderStatus.DRAFT)
                .createdBy(attorney)
                .build());

        testEvidenceItems = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            EvidenceItem item = evidenceItemRepository.save(EvidenceItem.builder()
                    .legalCase(legalCase)
                    .itemTrackingCode("EV-BATES-" + i)
                    .name("Evidence Document #" + i)
                    .evidenceType(EvidenceType.DOCUMENT)
                    .sourceOrigin("Law Office Vault")
                    .status(EvidenceStatus.AVAILABLE)
                    .storageUrl("evidence/test/doc" + i + ".pdf")
                    .fileSizeBytes(1024L)
                    .sha256Hash("hash-doc-" + i)
                    .uploadedBy(attorney)
                    .build());
            testEvidenceItems.add(item);
        }
    }

    @Test
    @DisplayName("Pessimistic lock on binder prevents Bates page collision across concurrent attorneys")
    void concurrentAddExhibits_PessimisticLockEnsuresContiguousBatesPages() throws InterruptedException {
        int numberOfAttorneys = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfAttorneys);
        CountDownLatch readyLatch = new CountDownLatch(numberOfAttorneys);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfAttorneys);

        AtomicInteger successfulAdds = new AtomicInteger(0);

        for (int i = 0; i < numberOfAttorneys; i++) {
            final int index = i;
            final Long evidenceId = testEvidenceItems.get(i).getId();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    log.info(">>> [Attorney-Worker-{}] Attempting to add exhibit for evidence ID: {}", index + 1, evidenceId);
                    AddExhibitRequest request = AddExhibitRequest.builder()
                            .evidenceId(evidenceId)
                            .exhibitLabel("EXHIBIT-" + (char) ('A' + index))
                            .pageCount(10) // Each exhibit has 10 pages
                            .build();

                    binderService.addExhibitToBinder(testBinder.getId(), request);
                    successfulAdds.incrementAndGet();
                    log.info("<<< [Attorney-Worker-{}] Exhibit added successfully", index + 1);
                } catch (Exception ex) {
                    log.error("<<< [Attorney-Worker-{}] Failed to add exhibit: {}", index + 1, ex.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successfulAdds.get()).isEqualTo(3);

        var exhibits = binderExhibitRepository.findByBinderIdOrderByBatesStartNumAsc(testBinder.getId());
        assertThat(exhibits).hasSize(3);

        // Verify strictly sequential, non-overlapping Bates numbering across all 3 exhibits
        assertThat(exhibits.get(0).getBatesStartNum()).isEqualTo(1);
        assertThat(exhibits.get(0).getBatesEndNum()).isEqualTo(10);

        assertThat(exhibits.get(1).getBatesStartNum()).isEqualTo(11);
        assertThat(exhibits.get(1).getBatesEndNum()).isEqualTo(20);

        assertThat(exhibits.get(2).getBatesStartNum()).isEqualTo(21);
        assertThat(exhibits.get(2).getBatesEndNum()).isEqualTo(30);

        log.info("CONCURRENCY VERIFIED: All 3 exhibits allocated strictly contiguous Bates ranges: 1-10, 11-20, 21-30 with ZERO overlap!");
    }
}
