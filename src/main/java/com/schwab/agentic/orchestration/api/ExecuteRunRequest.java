package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ExecuteRunRequest(
        @Schema(description = "PARALLEL or SEQUENTIAL. Omission of the entire request body defaults to PARALLEL.")
                @NotNull
                ExecutionMode mode) {
}
