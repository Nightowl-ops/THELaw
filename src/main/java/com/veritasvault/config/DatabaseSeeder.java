package com.veritasvault.config;

import com.veritasvault.model.ChainOfCustodyLog;
import com.veritasvault.model.EvidenceItem;
import com.veritasvault.model.LegalCase;
import com.veritasvault.model.User;
import com.veritasvault.model.enums.*;
import com.veritasvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.repository.EvidenceItemRepository;
import com.veritasvault.repository.LegalCaseRepository;
import com.veritasvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Database seeder executing at application startup.
 * Automatically provisions initial verified accounts across all roles,
 * benchmark legal cases, and sample evidence with baseline hashes.
 */
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final ChainOfCustodyLogRepository custodyLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // Database already contains seed or runtime data
        }

        // 1. Seed Users (Passwords set to 'Password123!')
        String defaultHashedPassword = passwordEncoder.encode("Password123!");

        User admin = User.builder()
                .fullName("System Administrator")
                .email("admin@veritasvault.com")
                .passwordHash(defaultHashedPassword)
                .role(Role.ROLE_ADMIN)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();

        User attorney = User.builder()
                .fullName("Sarah Jenkins, Esq.")
                .email("attorney@veritasvault.com")
                .passwordHash(defaultHashedPassword)
                .role(Role.ROLE_ATTORNEY)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();

        User examiner = User.builder()
                .fullName("Marcus Vance, CCE")
                .email("examiner@veritasvault.com")
                .passwordHash(defaultHashedPassword)
                .role(Role.ROLE_FORENSIC_EXAMINER)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();

        User client = User.builder()
                .fullName("David Sterling")
                .email("client@veritasvault.com")
                .passwordHash(defaultHashedPassword)
                .role(Role.ROLE_CLIENT)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();

        userRepository.save(admin);
        User savedAttorney = userRepository.save(attorney);
        User savedExaminer = userRepository.save(examiner);
        User savedClient = userRepository.save(client);

        // 2. Seed Benchmark Legal Case
        LegalCase benchmarkCase = LegalCase.builder()
                .caseNumber("CR-2026-0041")
                .caseTitle("State v. Sterling")
                .caseType(CaseType.DEFENSE)
                .status(CaseStatus.ACTIVE)
                .leadAttorney(savedAttorney)
                .client(savedClient)
                .opposingPartyName("State Department of Justice")
                .opposingCounselName("Eleanor Vance, Deputy DA")
                .opposingCounselEmail("evance@prosecution.gov")
                .build();

        LegalCase savedCase = legalCaseRepository.save(benchmarkCase);

        // 3. Seed Ingested Evidence Item
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
                .storageUrl("evidence/case-" + savedCase.getId() + "/seed-sample-ufed.bin")
                .fileSizeBytes(4294967296L)
                .sha256Hash(sampleSha256)
                .status(EvidenceStatus.AVAILABLE)
                .uploadedBy(savedAttorney)
                .build();

        EvidenceItem savedEvidence = evidenceItemRepository.save(evidence);

        // 4. Seed Initial Chain of Custody Ingest Record
        ChainOfCustodyLog initialLog = ChainOfCustodyLog.builder()
                .evidenceItem(savedEvidence)
                .actor(savedAttorney)
                .actionType(CustodyActionType.INGEST)
                .notes("Seed evidence intake. Baseline SHA-256 fingerprint verified against primary evidence locker.")
                .hashAtEvent(sampleSha256)
                .build();

        custodyLogRepository.save(initialLog);
    }
}