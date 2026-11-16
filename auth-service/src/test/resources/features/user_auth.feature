Feature: User Authentication & Registration

  Scenario: Registering a new user with valid credentials
    Given a registration request with email "clerk@veritasvault.com" and full name "Legal Clerk"
    And a valid secure password "Secret123!"
    When the user submits the registration request
    Then the registration should succeed
    And the registered user email should be "clerk@veritasvault.com"
    And the user role should be "ROLE_CLIENT"
