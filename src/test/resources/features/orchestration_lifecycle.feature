Feature: Orchestration approval lifecycle, failure handling, replanning and input validation
  Human approval, resume, rejection, safe-stop, bounded retries and replanning are exercised through the REST API.

  Scenario: Approving the brownfield checkpoint resumes execution to completion
    Given I start scenario "brownfield" in "SEQUENTIAL" mode
    Then the lifecycle run status is "AWAITING_APPROVAL"
    When every approval checkpoint is approved by "bdd-reviewer" and the run is resumed in "SEQUENTIAL" mode
    Then the lifecycle run status is "SUCCEEDED"
    And the run audit hash chain verifies

  Scenario: Rejecting the brownfield checkpoint stops the run without executing the gated task
    Given I start scenario "brownfield" in "SEQUENTIAL" mode
    When the pending approval is decided "REJECT" by "bdd-reviewer" with rationale "Change is out of scope"
    Then the approval decision response status is 200
    When the run is resumed in "SEQUENTIAL" mode
    Then the lifecycle run status is not "SUCCEEDED"
    And lifecycle task "implementation" is not in state "SUCCEEDED"

  Scenario: Safe-stop halts an approved run before dispatch and resume then completes it
    Given I start scenario "brownfield" in "SEQUENTIAL" mode
    When the pending approval is decided "APPROVE" by "bdd-reviewer" with rationale "Reviewed and approved"
    And a safe-stop is requested for the run
    And the run is executed in "SEQUENTIAL" mode
    Then the lifecycle run status is "SAFE_STOPPED"
    When the run is resumed in "SEQUENTIAL" mode
    Then the lifecycle run status is "SUCCEEDED"

  Scenario: A decided approval cannot be decided a second time
    Given I start scenario "brownfield" in "SEQUENTIAL" mode
    When the pending approval is decided "APPROVE" by "bdd-reviewer" with rationale "Reviewed and approved"
    And the pending approval is decided "REJECT" by "bdd-reviewer" with rationale "Changed my mind"
    Then the approval decision response status is 409

  Scenario: A task without a registered handler exhausts its bounded retries and fails the run
    Given I create a custom run with a single "CUSTOM" task named "unhandled"
    When the run is executed in "SEQUENTIAL" mode
    Then the lifecycle run status is "FAILED"
    And lifecycle task "unhandled" is in state "FAILED"
    And lifecycle task "unhandled" has exhausted its configured retry limit

  Scenario: Replanning is refused while a run awaits approval
    Given I start scenario "ambiguous" in "SEQUENTIAL" mode
    When the run is replanned with a single "REQUIREMENTS" task named "clarified-requirements"
    Then the replan response status is 409

  Scenario: Replanning is refused while a safe-stop request is still pending
    Given I start scenario "brownfield" in "SEQUENTIAL" mode
    When the pending approval is decided "APPROVE" by "bdd-reviewer" with rationale "Reviewed and approved"
    And a safe-stop is requested for the run
    And the run is executed in "SEQUENTIAL" mode
    Then the lifecycle run status is "SAFE_STOPPED"
    When the run is replanned with a single "REQUIREMENTS" task named "clarified-requirements"
    Then the replan response status is 409

  Scenario: A completed run can be replanned and the new task graph is recorded
    Given I start scenario "greenfield" in "SEQUENTIAL" mode
    Then the lifecycle run status is "SUCCEEDED"
    When the run is replanned with a single "REQUIREMENTS" task named "clarified-requirements"
    Then the replan response status is 200
    And lifecycle task "clarified-requirements" exists in the run

  Scenario: Invalid orchestration requests are rejected with client errors
    When I create a run with no tasks
    Then the lifecycle response status is 400
    When I run an unknown scenario "does-not-exist"
    Then the lifecycle response status is 400
    When I decide an unknown approval
    Then the lifecycle response status is 404
    When I decide a pending approval with a blank approver
    Then the lifecycle response status is 400

  Scenario: Operational endpoints that expose configuration are not published
    When I request the actuator endpoint "env"
    Then the lifecycle response status is 404
    When I request the actuator endpoint "heapdump"
    Then the lifecycle response status is 404
