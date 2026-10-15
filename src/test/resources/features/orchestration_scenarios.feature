Feature: Assessment orchestration scenarios
  Demonstration runs persist task state and stop at the appropriate human approval boundary.

  Scenario: Greenfield work completes without a high-impact approval
    When I run scenario "greenfield" in "SEQUENTIAL" mode
    Then scenario run status is "SUCCEEDED"
    And there are no pending approvals

  Scenario: Brownfield enhancement waits for approval after baseline validation
    When I run scenario "brownfield" in "SEQUENTIAL" mode
    Then scenario run status is "AWAITING_APPROVAL"
    And pending approval is for task "implementation"
    And task "baseline-validation" has state "SUCCEEDED"

  Scenario: Ambiguous requirements pause for human clarification
    When I run scenario "ambiguous" in "PARALLEL" mode
    Then scenario run status is "AWAITING_APPROVAL"
    And pending approval is for task "human-clarification"
