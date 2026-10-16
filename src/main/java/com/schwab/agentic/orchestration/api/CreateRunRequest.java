package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.dag.TaskDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRunRequest(
        @Schema(description = "Scenario or workflow identifier.", example = "brownfield")
                @NotBlank
                @Size(max = 64)
                String scenarioId,
        @Schema(description = "Optional run requirements or input summary.", example = "Add default URL expiry.")
        @Size(max = 4000) String inputSummary,
        @Schema(description = "Non-empty task list with explicit dependency identifiers.")
        @NotEmpty List<TaskDefinition> tasks) {

    public CreateRunRequest {
        if (tasks != null) {
            tasks = List.copyOf(tasks);
        }
    }
}
