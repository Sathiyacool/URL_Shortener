package com.schwab.agentic.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import com.schwab.agentic.shortener.repository.ClickEventRepository;
import com.schwab.agentic.shortener.repository.ShortUrlRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalRequestRepository approvalRepository;

    @Autowired
    private DecisionRecordRepository decisionRepository;

    @Autowired
    private ExecutionEventRepository eventRepository;

    @Autowired
    private TaskExecutionRepository taskRepository;

    @Autowired
    private ExecutionRunRepository runRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @BeforeEach
    void clearState() {
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
        clickEventRepository.deleteAll();
        shortUrlRepository.deleteAll();
    }

    @Test
    void listsAndRunsAllThreeDemonstrationScenarios() throws Exception {
        mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("greenfield"))
                .andExpect(jsonPath("$[1].id").value("brownfield"))
                .andExpect(jsonPath("$[2].id").value("ambiguous"));

        mockMvc.perform(post("/api/scenarios/greenfield/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executionMode":"SEQUENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.pendingApprovals.length()").value(0));

        MvcResult ambiguousResult = mockMvc.perform(post("/api/scenarios/ambiguous/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executionMode":"PARALLEL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.pendingApprovals[0].taskId").value("human-clarification"))
                .andReturn();
        JsonNode ambiguous = objectMapper.readTree(ambiguousResult.getResponse().getContentAsString());
        UUID ambiguousApprovalId = UUID.fromString(
                ambiguous.at("/pendingApprovals/0/approvalId").asText());
        mockMvc.perform(post("/api/approvals/{approvalId}", ambiguousApprovalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT","approver":"reviewer","rationale":"Retention behavior needs product review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void brownfieldScenarioVerifiesBaselineAndRequiresApprovalBeforeEnhancement() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/scenarios/brownfield/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"executionMode":"SEQUENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.pendingApprovals[0].taskId").value("implementation"))
                .andReturn();

        JsonNode response = objectMapper.readTree(started.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", taskState(response.path("run"), "baseline-validation"));
        UUID runId = UUID.fromString(response.path("runId").asText());
        UUID approvalId = UUID.fromString(response.at("/pendingApprovals/0/approvalId").asText());

        mockMvc.perform(post("/api/approvals/{approvalId}", approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","approver":"reviewer","rationale":"Approved 30-day default expiration with explicit expiry precedence"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/runs/{runId}/resume", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SEQUENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        MvcResult runResult = mockMvc.perform(get("/api/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode run = objectMapper.readTree(runResult.getResponse().getContentAsString());
        assertEquals("SUCCEEDED", taskState(run, "expiration-regression"));
        assertTrue(run.path("inputSummary").asText().contains("30-day"));

        mockMvc.perform(get("/api/runs/{runId}/audit", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").isNumber())
                .andExpect(jsonPath("$.decisions.length()").isNumber());
        assertTrue(clickEventRepository.count() > 0, "The baseline and regression checks should exercise analytics");
    }

    @Test
    void runApiSupportsCreateGetReplanSafeStopAndResume() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarioId":"api-test",
                                  "inputSummary":"Initial requirement",
                                  "tasks":[
                                    {"taskId":"documentation","kind":"DOCUMENTATION","dependencyIds":[],"impactLevel":"LOW"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        UUID runId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("runId").asText());

        mockMvc.perform(get("/api/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].taskId").value("documentation"));

        mockMvc.perform(post("/api/runs/{runId}/replan", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "updatedInputSummary":"Revised requirement",
                                  "tasks":[
                                    {"taskId":"documentation","kind":"DOCUMENTATION","dependencyIds":[],"impactLevel":"LOW"},
                                    {"taskId":"review","kind":"REVIEW","dependencyIds":["documentation"],"impactLevel":"LOW"}
                                  ],
                                  "requestedBy":"integration-test",
                                  "rationale":"Add review coverage for the revised requirement"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedTaskIds[0]").value("review"));

        mockMvc.perform(post("/api/runs/{runId}/safe-stop", runId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/runs/{runId}/execute", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAFE_STOPPED"));
        mockMvc.perform(post("/api/runs/{runId}/resume", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SEQUENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/runs/{runId}/audit", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private String taskState(JsonNode run, String taskId) {
        for (JsonNode task : run.path("tasks")) {
            if (task.path("taskId").asText().equals(taskId)) {
                return task.path("state").asText();
            }
        }
        throw new AssertionError("Task not found: " + taskId);
    }
}
