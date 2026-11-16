package com.veritasvault.caseevidence.bdd;

import com.veritasvault.caseevidence.client.AuthServiceClient;
import com.veritasvault.caseevidence.client.UserSummaryDto;
import com.veritasvault.caseevidence.dto.request.CreateCaseRequest;
import com.veritasvault.caseevidence.dto.response.CaseResponse;
import com.veritasvault.caseevidence.model.LegalCase;
import com.veritasvault.caseevidence.model.enums.CaseStatus;
import com.veritasvault.caseevidence.model.enums.CaseType;
import com.veritasvault.caseevidence.repository.LegalCaseRepository;
import com.veritasvault.caseevidence.security.UserPrincipal;
import com.veritasvault.caseevidence.service.LegalCaseService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CaseStepDefinitions {

    private final LegalCaseRepository legalCaseRepository = Mockito.mock(LegalCaseRepository.class);
    private final AuthServiceClient authServiceClient = Mockito.mock(AuthServiceClient.class);

    private final LegalCaseService legalCaseService;
    private CreateCaseRequest request;
    private UserPrincipal currentUser;
    private CaseResponse response;

    public CaseStepDefinitions() {
        this.legalCaseService = new LegalCaseService(legalCaseRepository, authServiceClient);
    }

    @Given("an attorney creates a case request with case number {string} and title {string}")
    public void an_attorney_creates_a_case_request(String caseNumber, String title) {
        request = new CreateCaseRequest();
        request.setCaseNumber(caseNumber);
        request.setCaseTitle(title);
        currentUser = UserPrincipal.builder()
                .userId(10L)
                .email("attorney@veritas.law")
                .role("ROLE_ATTORNEY")
                .build();
    }

    @And("the case type is {string}")
    public void the_case_type_is(String caseType) {
        request.setCaseType(CaseType.valueOf(caseType));
    }

    @And("the lead attorney ID is {long}")
    public void the_lead_attorney_id_is(Long attorneyId) {
        request.setLeadAttorneyId(attorneyId);
    }

    @When("the case creation request is processed")
    public void the_case_creation_request_is_processed() {
        when(legalCaseRepository.existsByCaseNumber(request.getCaseNumber())).thenReturn(false);

        UserSummaryDto attorneyDto = UserSummaryDto.builder()
                .id(10L)
                .email("attorney@veritas.law")
                .fullName("Harvey Specter")
                .role("ROLE_ATTORNEY")
                .status("ACTIVE")
                .build();
        when(authServiceClient.getUserById(10L)).thenReturn(attorneyDto);

        when(legalCaseRepository.save(any(LegalCase.class))).thenAnswer(inv -> {
            LegalCase c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        response = legalCaseService.createCase(request, currentUser);
    }

    @Then("the case should be created successfully")
    public void the_case_should_be_created_successfully() {
        assertNotNull(response);
        assertEquals(101L, response.getId());
    }

    @And("the case status should be {string}")
    public void the_case_status_should_be(String expectedStatus) {
        assertEquals(expectedStatus, response.getStatus().name());
    }

    @And("the returned case number should be {string}")
    public void the_returned_case_number_should_be(String expectedCaseNumber) {
        assertEquals(expectedCaseNumber, response.getCaseNumber());
    }
}
