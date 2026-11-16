package com.veritasvault.caseevidence.config;

import com.veritasvault.caseevidence.model.EvidenceItem;
import com.veritasvault.caseevidence.model.LegalCase;
import com.veritasvault.caseevidence.model.enums.CaseStatus;
import com.veritasvault.caseevidence.model.enums.CaseType;
import com.veritasvault.caseevidence.model.enums.EvidenceStatus;
import com.veritasvault.caseevidence.model.enums.EvidenceType;
import com.veritasvault.caseevidence.repository.EvidenceItemRepository;
import com.veritasvault.caseevidence.repository.LegalCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final LegalCaseRepository legalCaseRepository;
    private final EvidenceItemRepository evidenceItemRepository;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public void run(String... args) {
        if (legalCaseRepository.count() > 0) {
            log.info("Legal cases already exist in veritas_db. Skipping Case & Evidence seeding.");
            return;
        }

        log.info("Seeding benchmark Legal Case and Evidence items in veritas_db...");

        // 1. Seed Benchmark Legal Case: State v. Sterling
        LegalCase benchmarkCase = LegalCase.builder()
                .caseNumber("CR-2026-0041")
                .caseTitle("State v. Sterling")
                .caseType(CaseType.DEFENSE)
                .status(CaseStatus.ACTIVE)
                .leadAttorneyId(2L) // Sarah Jenkins, Esq.
                .clientId(4L)       // David Sterling
                .opposingPartyName("State Department of Justice")
                .opposingCounselName("Eleanor Vance, Deputy DA")
                .opposingCounselEmail("evance@prosecution.gov")
                .build();

        LegalCase savedCase = legalCaseRepository.save(benchmarkCase);

        // 2. Prepare physical dummy sample file on disk
        String relativeStoragePath = "evidence/case-" + savedCase.getId() + "/seed-sample-ufed.bin";
        try {
            File targetFile = new File(uploadDir, relativeStoragePath);
            targetFile.getParentFile().mkdirs();
            if (!targetFile.exists()) {
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write("VERITAS-FORENSIC-DUMMY-EXTRACTION-STREAM-CONTENT".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            log.warn("Could not create physical seed file on disk: {}", e.getMessage());
        }

        // 3. Seed Benchmark Evidence Item: Cellebrite UFED Image
        String sampleSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        EvidenceItem evidence = EvidenceItem.builder()
                .legalCase(savedCase)
                .itemTrackingCode("EV-2026-0001")
                .name("Forensic Phone Extraction - iPhone 15 Pro")
                .description("Physical Cellebrite UFED extraction dump containing encrypted logs and call records.")
                .evidenceType(EvidenceType.DEVICE_IMAGE)
                .sourceOrigin("Officer Locker #12 - Seized at Incident Scene")
                .seizingOfficerName("Detective Raymond Miller")
                .officerBadgeNumber("BADGE-8842")
                .lawEnforcementAgency("Metropolitan Police Department")
                .policeIncidentReportNum("IR-2026-90412")
                .storageUrl(relativeStoragePath)
                .fileSizeBytes(4294967296L)
                .sha256Hash(sampleSha256)
                .status(EvidenceStatus.AVAILABLE)
                .uploadedByUserId(2L) // Sarah Jenkins, Esq.
                .build();

        evidenceItemRepository.save(evidence);

        log.info("Benchmark Case ('{}') and Evidence ('{}') successfully seeded into veritas_db.",
                savedCase.getCaseNumber(), evidence.getItemTrackingCode());
    }
}
