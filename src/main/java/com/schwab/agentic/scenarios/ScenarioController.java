package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping
    public List<ScenarioDescriptor> list() {
        return scenarioService.list();
    }

    @PostMapping("/{scenarioId}/run")
    public ScenarioRunResponse run(
            @PathVariable String scenarioId, @Valid @RequestBody(required = false) ScenarioRunRequest request) {
        return scenarioService.run(
                scenarioId, request == null ? ExecutionMode.PARALLEL : request.executionMode());
    }

    public record ScenarioRunRequest(@NotNull ExecutionMode executionMode) {
    }
}
