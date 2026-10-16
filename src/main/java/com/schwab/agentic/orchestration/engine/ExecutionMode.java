package com.schwab.agentic.orchestration.engine;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Execution scheduling mode.")
public enum ExecutionMode {
    SEQUENTIAL,
    PARALLEL
}
