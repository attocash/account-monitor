Feature: Account

  Scenario: Account is monitored
    Given an account is monitored

    When address is disabled
    And account should be disabled
    And address is enabled
    And account should be enabled

    When a transaction is published
    And an account entry is published

    Then transaction is persisted
    And account entry is persisted
