package com.schwab.agentic.orchestration.dag;

import java.util.List;
import java.util.Map;

public final class TaskDag {

    private final Map<String, TaskDefinition> tasksById;
    private final List<String> topologicalOrder;

    TaskDag(Map<String, TaskDefinition> tasksById, List<String> topologicalOrder) {
        this.tasksById = Map.copyOf(tasksById);
        this.topologicalOrder = List.copyOf(topologicalOrder);
    }

    public Map<String, TaskDefinition> tasksById() {
        return tasksById;
    }

    public List<String> topologicalOrder() {
        return topologicalOrder;
    }
}
