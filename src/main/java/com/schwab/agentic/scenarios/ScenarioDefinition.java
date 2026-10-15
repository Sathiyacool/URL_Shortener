package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.dag.TaskDefinition;
import java.util.List;
import java.util.Locale;

public record ScenarioDefinition(
        ScenarioId id,
        String name,
        String description,
        String inputSummary,
        List<TaskDefinition> tasks) {

    public ScenarioDefinition {
        tasks = List.copyOf(tasks);
    }

    public ScenarioDescriptor descriptor() {
        return new ScenarioDescriptor(id.name().toLowerCase(Locale.ROOT), name, description, tasks.size());
    }
}
