Feature: Forensic Evidence Cryptographic Verification & Tamper Detection

  Scenario: Detecting a compromised evidence item when SHA-256 hashes mismatch
    Given an evidence item with ID 50 has an intake hash of "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    When the forensic examiner performs check-in with computed hash "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0"
    Then the integrity status must be marked as "COMPROMISED"
    And a Kafka tamper event should be published for quarantine
