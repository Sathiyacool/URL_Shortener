package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.engine.ExecutionMode;
import jakarta.validation.constraints.NotNull;

public record ExecuteRunRequest(@NotNull ExecutionMode mode) {
}
