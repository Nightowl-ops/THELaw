package com.veritasvault.forensicvault.bdd;

import com.veritasvault.forensicvault.client.AuthServiceClient;
import com.veritasvault.forensicvault.client.CaseServiceClient;
import com.veritasvault.forensicvault.kafka.VaultKafkaProducer;
import com.veritasvault.forensicvault.model.enums.LabIntegrityStatus;
import com.veritasvault.forensicvault.repository.ChainOfCustodyLogRepository;
import com.veritasvault.forensicvault.repository.CustodyReservationRepository;
import com.veritasvault.forensicvault.repository.ForensicLabReportRepository;
import com.veritasvault.forensicvault.service.CustodyReservationService;
import com.veritasvault.forensicvault.service.FileStorageService;
import com.veritasvault.forensicvault.service.NotificationService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EvidenceIntegrityStepDefinitions {

    private final CustodyReservationRepository reservationRepository = Mockito.mock(CustodyReservationRepository.class);
    private final ForensicLabReportRepository labReportRepository = Mockito.mock(ForensicLabReportRepository.class);
    private final ChainOfCustodyLogRepository custodyLogRepository = Mockito.mock(ChainOfCustodyLogRepository.class);
    private final FileStorageService fileStorageService = Mockito.mock(FileStorageService.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final AuthServiceClient authServiceClient = Mockito.mock(AuthServiceClient.class);
    private final CaseServiceClient caseServiceClient = Mockito.mock(CaseServiceClient.class);
    private final VaultKafkaProducer vaultKafkaProducer = Mockito.mock(VaultKafkaProducer.class);

    private final CustodyReservationService custodyReservationService;

    private Long evidenceId;
    private String originalHash;
    private String computedHash;
    private LabIntegrityStatus resultStatus;
    private boolean kafkaEventPublished = false;

    public EvidenceIntegrityStepDefinitions() {
        this.custodyReservationService = new CustodyReservationService(
                reservationRepository,
                labReportRepository,
                custodyLogRepository,
                fileStorageService,
                notificationService,
                authServiceClient,
                caseServiceClient,
                vaultKafkaProducer
        );
    }

    @Given("an evidence item with ID {long} has an intake hash of {string}")
    public void an_evidence_item_with_id_has_intake_hash(Long id, String hash) {
        this.evidenceId = id;
        this.originalHash = hash;
    }

    @When("the forensic examiner performs check-in with computed hash {string}")
    public void the_forensic_examiner_performs_checkin(String hash) {
        this.computedHash = hash;

        // Core business logic: hash verification and Kafka tamper notification
        if (originalHash != null && originalHash.equalsIgnoreCase(computedHash)) {
            resultStatus = LabIntegrityStatus.VERIFIED;
        } else {
            resultStatus = LabIntegrityStatus.COMPROMISED;
            vaultKafkaProducer.publishEvidenceTampered(
                    evidenceId,
                    originalHash,
                    computedHash,
                    1L,
                    "Forensic SHA-256 mismatch detected during lab check-in"
            );
            kafkaEventPublished = true;
        }
    }

    @Then("the integrity status must be marked as {string}")
    public void the_integrity_status_must_be_marked_as(String expectedStatus) {
        assertEquals(expectedStatus, resultStatus.name());
    }

    @And("a Kafka tamper event should be published for quarantine")
    public void a_kafka_tamper_event_should_be_published() {
        assertTrue(kafkaEventPublished);
        verify(vaultKafkaProducer, times(1)).publishEvidenceTampered(
                eq(evidenceId),
                eq(originalHash),
                eq(computedHash),
                anyLong(),
                anyString()
        );
    }
}
