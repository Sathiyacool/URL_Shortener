package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.dag.TaskKind;

public interface TaskFallbackHandler {

    TaskKind kind();

    TaskResult execute(TaskContext context, Exception primaryFailure) throws Exception;
}
