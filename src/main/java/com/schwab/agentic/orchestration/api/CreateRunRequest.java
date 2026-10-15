package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.dag.TaskDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRunRequest(
        @NotBlank @Size(max = 64) String scenarioId,
        @Size(max = 4000) String inputSummary,
        @NotEmpty List<TaskDefinition> tasks) {

    public CreateRunRequest {
        if (tasks != null) {
            tasks = List.copyOf(tasks);
        }
    }
}
