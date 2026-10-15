package com.schwab.agentic.orchestration.approval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.ActorType;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private ApprovalService approvalService;

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

    @BeforeEach
    void clearState() {
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void listsAndApprovesPendingRequestWithDecisionLineage() throws Exception {
        UUID runId = createHighImpactRun();
        ApprovalRequestEntity request = approvalService.requestApproval(runId, "api-change", "Change public API");

        mockMvc.perform(get("/api/approvals/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalId").value(request.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        mockMvc.perform(post("/api/approvals/" + request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","approver":"reviewer","rationale":"Reviewed and approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approver").value("reviewer"));

        ApprovalRequestEntity saved = approvalRepository.findById(request.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getDecisionRecordId());
        org.junit.jupiter.api.Assertions.assertEquals(
                saved.getDecisionRecordId(),
                decisionRepository.findAll().stream()
                        .filter(decision -> decision.getKind() == DecisionKind.APPROVAL
                                && decision.getActorType() == ActorType.HUMAN)
                        .findFirst()
                        .orElseThrow()
                        .getId());
    }

    @Test
    void rejectsDuplicateApprovalDecision() throws Exception {
        UUID runId = createHighImpactRun();
        ApprovalRequestEntity request = approvalService.requestApproval(runId, "api-change", "Change public API");
        approvalService.decide(request.getId(), ApprovalDecision.APPROVE, "reviewer", "Approved");

        mockMvc.perform(post("/api/approvals/" + request.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT","approver":"reviewer","rationale":"Duplicate decision"}
                                """))
                .andExpect(status().isConflict());
    }

    private UUID createHighImpactRun() {
        UUID runId = persistenceService.createRun(
                "guardrail-test",
                "Test approval endpoint",
                dagValidator.validate(List.of(new TaskDefinition(
                        "api-change", TaskKind.CODE, Set.of(), ImpactLevel.HIGH))));
        persistenceService.transitionRun(
                runId, RunStatus.RUNNING, ActorType.SYSTEM, "scheduler", "Start run");
        return runId;
    }
}
