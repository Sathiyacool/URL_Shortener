package com.schwab.agentic.orchestration.engine;

public class TaskStoppedException extends RuntimeException {

    public TaskStoppedException() {
        super("Task execution was stopped");
    }
}
