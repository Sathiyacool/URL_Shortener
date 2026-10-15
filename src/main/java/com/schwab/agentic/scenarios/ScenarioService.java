package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.api.CreateRunRequest;
import com.schwab.agentic.orchestration.api.OrchestrationRunService;
import com.schwab.agentic.orchestration.engine.ExecutionMode;
import com.schwab.agentic.orchestration.engine.ExecutionSummary;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

    private final ScenarioCatalog catalog;
    private final OrchestrationRunService runService;

    public ScenarioService(ScenarioCatalog catalog, OrchestrationRunService runService) {
        this.catalog = catalog;
        this.runService = runService;
    }

    public List<ScenarioDescriptor> list() {
        return catalog.list();
    }

    public ScenarioRunResponse run(String scenarioId, ExecutionMode executionMode) {
        return run(catalog.get(scenarioId), executionMode);
    }

    public ScenarioRunResponse run(ScenarioId scenarioId, ExecutionMode executionMode) {
        return run(catalog.get(scenarioId), executionMode);
    }

    private ScenarioRunResponse run(ScenarioDefinition scenario, ExecutionMode executionMode) {
        if (executionMode == null) {
            throw new IllegalArgumentException("Execution mode is required");
        }
        var request = new CreateRunRequest(
                scenario.id().name().toLowerCase(java.util.Locale.ROOT),
                scenario.inputSummary(),
                scenario.tasks());
        var runId = runService.create(request);
        ExecutionSummary summary = runService.execute(runId, executionMode);
        return new ScenarioRunResponse(
                scenario.id().name().toLowerCase(java.util.Locale.ROOT),
                runId,
                summary.status(),
                summary.taskCounts(),
                summary.failureReason(),
                runService.pendingApprovals(runId),
                runService.get(runId));
    }
}
