package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.dag.TaskKind;

public interface TaskHandler {

    TaskKind kind();

    TaskResult execute(TaskContext context) throws Exception;

    default void compensate(TaskContext context, TaskResult completedResult) throws Exception {
    }
}
