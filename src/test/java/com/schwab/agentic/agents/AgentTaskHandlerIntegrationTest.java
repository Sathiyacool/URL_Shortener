package com.schwab.agentic.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.schwab.agentic.guardrails.ArtifactVersionRepository;
import com.schwab.agentic.orchestration.approval.ApprovalRequestRepository;
import com.schwab.agentic.orchestration.dag.DagValidator;
import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import com.schwab.agentic.orchestration.domain.DecisionKind;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.engine.ExecutionEngine;
import com.schwab.agentic.orchestration.engine.OrchestrationPersistenceService;
import com.schwab.agentic.orchestration.repository.DecisionRecordRepository;
import com.schwab.agentic.orchestration.repository.ExecutionEventRepository;
import com.schwab.agentic.orchestration.repository.ExecutionRunRepository;
import com.schwab.agentic.orchestration.repository.TaskExecutionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AgentTaskHandlerIntegrationTest {

    private static final Path SANDBOX_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "agentic-agent-tests", UUID.randomUUID().toString());

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private DagValidator dagValidator;

    @Autowired
    private OrchestrationPersistenceService persistenceService;

    @Autowired
    private ArtifactVersionRepository artifactRepository;

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

    @DynamicPropertySource
    static void configureSandbox(DynamicPropertyRegistry registry) {
        registry.add("artifact.sandbox.root", SANDBOX_ROOT::toString);
    }

    @BeforeEach
    void clearDatabase() {
        artifactRepository.deleteAll();
        approvalRepository.deleteAll();
        decisionRepository.deleteAll();
        eventRepository.deleteAll();
        taskRepository.deleteAll();
        runRepository.deleteAll();
    }

    @AfterAll
    static void removeSandboxFiles() throws IOException {
        if (Files.exists(SANDBOX_ROOT)) {
            try (var paths = Files.walk(SANDBOX_ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void executesAllSevenAgentsAndPersistsTheirOutputsAndLineage() {
        UUID runId = persistenceService.createRun(
                "agent-smoke",
                "Implement URL expiration with safe analytics behavior",
                dagValidator.validate(List.of(
                        new TaskDefinition("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                        new TaskDefinition(
                                "architecture", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.LOW),
                        new TaskDefinition("implementation", TaskKind.CODE, Set.of("architecture"), ImpactLevel.MEDIUM),
                        new TaskDefinition("tests", TaskKind.TEST, Set.of("implementation"), ImpactLevel.LOW),
                        new TaskDefinition("review", TaskKind.REVIEW, Set.of("implementation"), ImpactLevel.LOW),
                        new TaskDefinition("security", TaskKind.SECURITY, Set.of("implementation"), ImpactLevel.LOW),
                        new TaskDefinition(
                                "documentation",
                                TaskKind.DOCUMENTATION,
                                Set.of("tests", "review", "security"),
                                ImpactLevel.LOW))));

        var summary = executionEngine.execute(runId);

        assertEquals(RunStatus.SUCCEEDED, summary.status());
        assertTrue(summary.taskCounts().getOrDefault("SUCCEEDED", 0) == 7);
        assertEquals(2, artifactRepository.findAllByRunIdAndTaskIdOrderByCreatedAtAsc(runId, "implementation").size()
                + artifactRepository.findAllByRunIdAndTaskIdOrderByCreatedAtAsc(runId, "documentation").size());
        assertEquals(9, decisionRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).size());
        assertEquals(
                7,
                decisionRepository.findAllByRunIdOrderByCreatedAtAscIdAsc(runId).stream()
                        .filter(decision -> decision.getKind() == DecisionKind.PLAN)
                        .count());
    }
}
