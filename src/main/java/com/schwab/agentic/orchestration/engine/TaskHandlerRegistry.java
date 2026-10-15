package com.schwab.agentic.orchestration.engine;

import com.schwab.agentic.orchestration.dag.TaskKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TaskHandlerRegistry {

    private final Map<TaskKind, TaskHandler> handlers;
    private final Map<TaskKind, TaskFallbackHandler> fallbackHandlers;

    public TaskHandlerRegistry(List<TaskHandler> handlers, List<TaskFallbackHandler> fallbackHandlers) {
        this.handlers = indexHandlers(handlers, TaskHandler::kind);
        this.fallbackHandlers = indexHandlers(fallbackHandlers, TaskFallbackHandler::kind);
    }

    public TaskHandler handlerFor(TaskKind kind) {
        TaskHandler handler = handlers.get(kind);
        if (handler == null) {
            throw new MissingTaskHandlerException(kind);
        }
        return handler;
    }

    public Optional<TaskFallbackHandler> fallbackFor(TaskKind kind) {
        return Optional.ofNullable(fallbackHandlers.get(kind));
    }

    private <T> Map<TaskKind, T> indexHandlers(List<T> instances, java.util.function.Function<T, TaskKind> kindFunction) {
        Map<TaskKind, T> indexed = new HashMap<>();
        for (T instance : instances) {
            TaskKind kind = kindFunction.apply(instance);
            if (kind == null || indexed.putIfAbsent(kind, instance) != null) {
                throw new IllegalStateException("Task handler kinds must be non-null and unique: " + kind);
            }
        }
        return Map.copyOf(indexed);
    }
}
