package com.schwab.agentic.llm;

import com.schwab.agentic.agents.AgentArtifact;
import com.schwab.agentic.agents.AgentRole;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockLlmProvider implements LlmProvider {

    private static final int MAX_OBJECTIVE_CHARS = 400;
    private static final int MAX_DEPENDENCY_CHARS = 500;

    @Override
    public LlmResponse generate(LlmRequest request) {
        String objective = truncate(request.objective().replaceAll("\\s+", " ").trim(), MAX_OBJECTIVE_CHARS);
        String dependencies = summarizeDependencies(request.dependencyOutputs());
        String summary = "Deterministic " + request.role().name().toLowerCase(Locale.ROOT)
                + " result for task " + request.taskId()
                + ". Objective: " + (objective.isBlank() ? "not specified" : objective)
                + ". Upstream context: " + dependencies + ".";
        List<AgentArtifact> artifacts = createsArtifact(request.role())
                ? List.of(new AgentArtifact(
                        artifactPath(request.role(), request.taskId()),
                        "# " + request.role().name() + " proposal\n\n"
                                + "Task: " + request.taskId() + "\n\n"
                                + "Objective: " + (objective.isBlank() ? "not specified" : objective) + "\n\n"
                                + "Upstream context: " + dependencies + "\n"))
                : List.of();
        return new LlmResponse(truncate(summary, 3000), 0.95, artifacts);
    }

    private String summarizeDependencies(Map<String, String> dependencyOutputs) {
        if (dependencyOutputs.isEmpty()) {
            return "none";
        }
        String joined = dependencyOutputs.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + truncate(entry.getValue(), 120))
                .reduce((left, right) -> left + "; " + right)
                .orElse("none");
        return truncate(joined, MAX_DEPENDENCY_CHARS);
    }

    private String artifactPath(AgentRole role, String taskId) {
        String taskHash = UUID.nameUUIDFromBytes(taskId.getBytes(StandardCharsets.UTF_8)).toString();
        String directory = role == AgentRole.DOCUMENTATION ? "docs" : "proposals";
        return "agent-output/" + directory + "/" + role.name().toLowerCase(Locale.ROOT) + "-" + taskHash + ".md";
    }

    private boolean createsArtifact(AgentRole role) {
        return role == AgentRole.CODER || role == AgentRole.DOCUMENTATION;
    }

    private String truncate(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }
}
