Feature: Legal Case Intake Management

  Scenario: Creating a new legal case with an assigned lead attorney
    Given an attorney creates a case request with case number "CASE-2026-CRIM-001" and title "State vs Anderson"
    And the case type is "DEFENSE"
    And the lead attorney ID is 10
    When the case creation request is processed
    Then the case should be created successfully
    And the case status should be "ACTIVE"
    And the returned case number should be "CASE-2026-CRIM-001"
