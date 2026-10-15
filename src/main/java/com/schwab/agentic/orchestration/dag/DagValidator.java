package com.schwab.agentic.orchestration.dag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.stereotype.Component;

@Component
public class DagValidator {

    public TaskDag validate(Collection<TaskDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new DagValidationException("A task DAG must contain at least one task");
        }

        Map<String, TaskDefinition> tasksById = new LinkedHashMap<>();
        for (TaskDefinition definition : definitions) {
            if (definition == null
                    || definition.taskId() == null
                    || definition.taskId().isBlank()
                    || definition.taskId().length() > 100) {
                throw new DagValidationException("Task IDs must contain 1 to 100 characters");
            }
            if (definition.kind() == null || definition.impactLevel() == null) {
                throw new DagValidationException("Task kind and impact level are required: " + definition.taskId());
            }
            if (tasksById.putIfAbsent(definition.taskId(), definition) != null) {
                throw new DagValidationException("Duplicate task ID: " + definition.taskId());
            }
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        tasksById.keySet().forEach(taskId -> {
            indegree.put(taskId, 0);
            dependents.put(taskId, new ArrayList<>());
        });

        for (TaskDefinition definition : tasksById.values()) {
            for (String dependencyId : definition.dependencyIds()) {
                if (!tasksById.containsKey(dependencyId)) {
                    throw new DagValidationException(
                            "Task " + definition.taskId() + " depends on unknown task: " + dependencyId);
                }
                if (definition.taskId().equals(dependencyId)) {
                    throw new DagValidationException("Task cannot depend on itself: " + definition.taskId());
                }
                indegree.compute(definition.taskId(), (ignored, count) -> count + 1);
                dependents.get(dependencyId).add(definition.taskId());
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((taskId, count) -> {
            if (count == 0) {
                ready.add(taskId);
            }
        });

        List<String> order = new ArrayList<>(tasksById.size());
        while (!ready.isEmpty()) {
            String taskId = ready.remove();
            order.add(taskId);
            for (String dependent : dependents.get(taskId)) {
                int remaining = indegree.compute(dependent, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() != tasksById.size()) {
            throw new DagValidationException("Task dependencies contain a cycle");
        }
        return new TaskDag(tasksById, order);
    }
}
