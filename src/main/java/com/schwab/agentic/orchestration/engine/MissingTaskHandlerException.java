package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.dag.TaskKind;

public class MissingTaskHandlerException extends IllegalStateException {

    public MissingTaskHandlerException(TaskKind kind) {
        super("No task handler is registered for kind " + kind);
    }
}
