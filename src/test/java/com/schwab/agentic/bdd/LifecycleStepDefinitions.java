package com.schwab.agentic.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.engine.RetryPolicy;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public class LifecycleStepDefinitions {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RetryPolicy retryPolicy;

    private MvcResult response;
    private MvcResult approvalResponse;
    private MvcResult replanResponse;
    private String runId;
    private String approvalId;

    @Given("I start scenario {string} in {string} mode")
    public void startScenario(String scenarioId, String mode) throws Exception {
        response = postJson("/api/scenarios/" + scenarioId + "/run", Map.of("executionMode", mode));
        assertEquals(200, response.getResponse().getStatus());
        runId = json(response).path("runId").asText();
        JsonNode approvals = json(response).path("pendingApprovals");
        approvalId = approvals.isEmpty() ? null : approvals.get(0).path("approvalId").asText();
    }

    @Given("I create a custom run with a single {string} task named {string}")
    public void createCustomRun(String kind, String taskId) throws Exception {
        response = postJson("/api/runs", Map.of(
                "scenarioId", "bdd-custom",
                "inputSummary", "BDD failure-handling run",
                "tasks", List.of(task(taskId, kind))));
        assertEquals(201, response.getResponse().getStatus());
        runId = json(response).path("runId").asText();
    }

    @When("the run is executed in {string} mode")
    public void executeCreatedRun(String mode) throws Exception {
        response = postJson("/api/runs/" + runId + "/execute", Map.of("mode", mode));
        assertEquals(200, response.getResponse().getStatus());
    }

    @When("the pending approval is decided {string} by {string} with rationale {string}")
    public void decideApproval(String decision, String approver, String rationale) throws Exception {
        assertTrue(approvalId != null, "A pending approval is required");
        approvalResponse = postJson("/api/approvals/" + approvalId,
                Map.of("decision", decision, "approver", approver, "rationale", rationale));
    }

    @When("the run is resumed in {string} mode")
    public void resumeRun(String mode) throws Exception {
        response = postJson("/api/runs/" + runId + "/resume", Map.of("mode", mode));
    }

    @When("every approval checkpoint is approved by {string} and the run is resumed in {string} mode")
    public void approveAllAndResume(String approver, String mode) throws Exception {
        for (int round = 0; round < 6; round++) {
            MvcResult pending = mockMvc.perform(get("/api/runs/{id}/approvals/pending", runId)).andReturn();
            JsonNode approvals = json(pending);
            if (approvals.isEmpty()) {
                return;
            }
            for (JsonNode approval : approvals) {
                approvalId = approval.path("approvalId").asText();
                decideApproval("APPROVE", approver, "Reviewed impact and rollback plan");
                assertEquals(200, approvalResponse.getResponse().getStatus());
            }
            resumeRun(mode);
            assertEquals(200, response.getResponse().getStatus());
        }
    }

    @When("a safe-stop is requested for the run")
    public void requestSafeStop() throws Exception {
        response = mockMvc.perform(post("/api/runs/{id}/safe-stop", runId)).andReturn();
        assertEquals(200, response.getResponse().getStatus());
    }

    @When("the run is replanned with a single {string} task named {string}")
    public void replan(String kind, String taskId) throws Exception {
        replanResponse = postJson("/api/runs/" + runId + "/replan", Map.of(
                "updatedInputSummary", "Requirements clarified by a human",
                "tasks", List.of(task(taskId, kind)),
                "requestedBy", "bdd-reviewer",
                "rationale", "Upstream requirement changed"));
    }

    @When("I create a run with no tasks")
    public void createRunWithNoTasks() throws Exception {
        response = postJson("/api/runs", Map.of("scenarioId", "bdd-invalid", "tasks", List.of()));
    }

    @When("I run an unknown scenario {string}")
    public void runUnknownScenario(String scenarioId) throws Exception {
        response = postJson("/api/scenarios/" + scenarioId + "/run", Map.of("executionMode", "SEQUENTIAL"));
    }

    @When("I decide an unknown approval")
    public void decideUnknownApproval() throws Exception {
        response = postJson("/api/approvals/" + UUID.randomUUID(),
                Map.of("decision", "APPROVE", "approver", "bdd-reviewer", "rationale", "No such approval"));
    }

    @When("I decide a pending approval with a blank approver")
    public void decideWithBlankApprover() throws Exception {
        startScenario("brownfield", "SEQUENTIAL");
        response = postJson("/api/approvals/" + approvalId,
                Map.of("decision", "APPROVE", "approver", " ", "rationale", "Blank approver"));
    }

    @When("I request the actuator endpoint {string}")
    public void requestActuator(String endpoint) throws Exception {
        response = mockMvc.perform(get("/actuator/" + endpoint)).andReturn();
    }

    @Then("the lifecycle run status is {string}")
    public void runStatusIs(String expected) throws Exception {
        assertEquals(expected, currentRunStatus());
    }

    @Then("the lifecycle run status is not {string}")
    public void runStatusIsNot(String unexpected) throws Exception {
        assertNotEquals(unexpected, currentRunStatus());
    }

    @Then("lifecycle task {string} is in state {string}")
    public void taskInState(String taskId, String state) throws Exception {
        assertEquals(state, findTask(taskId).path("state").asText());
    }

    @Then("lifecycle task {string} is not in state {string}")
    public void taskNotInState(String taskId, String state) throws Exception {
        assertNotEquals(state, findTask(taskId).path("state").asText());
    }

    @Then("lifecycle task {string} has exhausted its configured retry limit")
    public void taskExhaustedRetryLimit(String taskId) throws Exception {
        JsonNode task = findTask(taskId);
        assertEquals(retryPolicy.maxAttempts(), task.path("attemptCount").asInt());
        assertFalse(task.path("failureSummary").asText().isBlank(), "Expected a persisted failure summary");
    }

    @Then("lifecycle task {string} exists in the run")
    public void taskExists(String taskId) throws Exception {
        assertFalse(findTask(taskId).isMissingNode());
    }

    @Then("the run audit hash chain verifies")
    public void auditVerifies() throws Exception {
        MvcResult audit = mockMvc.perform(get("/api/runs/{id}/audit/verify", runId)).andReturn();
        assertEquals(200, audit.getResponse().getStatus());
        assertTrue(json(audit).path("valid").asBoolean(), audit.getResponse().getContentAsString());
    }

    @Then("the approval decision response status is {int}")
    public void approvalStatusIs(int expected) {
        assertEquals(expected, approvalResponse.getResponse().getStatus());
    }

    @Then("the replan response status is {int}")
    public void replanStatusIs(int expected) throws Exception {
        assertEquals(expected, replanResponse.getResponse().getStatus(), replanResponse.getResponse().getContentAsString());
    }

    @Then("the lifecycle response status is {int}")
    public void responseStatusIs(int expected) {
        assertEquals(expected, response.getResponse().getStatus());
    }

    private String currentRunStatus() throws Exception {
        MvcResult run = mockMvc.perform(get("/api/runs/{id}", runId)).andReturn();
        assertEquals(200, run.getResponse().getStatus());
        return json(run).path("status").asText();
    }

    private JsonNode findTask(String taskId) throws Exception {
        MvcResult run = mockMvc.perform(get("/api/runs/{id}", runId)).andReturn();
        for (JsonNode task : json(run).path("tasks")) {
            if (task.path("taskId").asText().equals(taskId)) {
                return task;
            }
        }
        return objectMapper.missingNode();
    }

    private Map<String, Object> task(String taskId, String kind) {
        return Map.of("taskId", taskId, "kind", kind, "dependencyIds", List.of(), "impactLevel", "LOW");
    }

    private MvcResult postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
