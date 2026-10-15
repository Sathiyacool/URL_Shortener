package com.schwab.agentic.orchestration.engine;

public record TaskResult(String output) {

    public TaskResult {
        output = output == null ? "" : output;
    }

    public static TaskResult completed(String output) {
        return new TaskResult(output);
    }
}
