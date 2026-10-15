package com.schwab.agentic.scenarios;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import com.schwab.agentic.orchestration.dag.TaskDefinition;
import com.schwab.agentic.orchestration.dag.TaskKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ScenarioCatalog {

    private final Map<ScenarioId, ScenarioDefinition> scenarios = Map.of(
            ScenarioId.GREENFIELD,
            new ScenarioDefinition(
                    ScenarioId.GREENFIELD,
                    "Greenfield URL shortener",
                    "Plan a Java URL shortener from a blank-slate request and produce sandbox proposals.",
                    "Build a Java URL shortener with HTTP redirects, expiration, and click analytics.",
                    List.of(
                            task("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                            task("architecture", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.LOW),
                            task("implementation", TaskKind.CODE, Set.of("architecture"), ImpactLevel.MEDIUM),
                            task("tests", TaskKind.TEST, Set.of("implementation"), ImpactLevel.LOW),
                            task("review", TaskKind.REVIEW, Set.of("implementation"), ImpactLevel.LOW),
                            task("security", TaskKind.SECURITY, Set.of("implementation"), ImpactLevel.LOW),
                            task(
                                    "documentation",
                                    TaskKind.DOCUMENTATION,
                                    Set.of("tests", "review", "security"),
                                    ImpactLevel.LOW))),
            ScenarioId.BROWNFIELD,
            new ScenarioDefinition(
                    ScenarioId.BROWNFIELD,
                    "Brownfield default expiration",
                    "Verify the existing shortener baseline, then introduce a configurable 30-day default TTL and regression checks.",
                    "Existing URL shortener baseline: create, redirect, and analytics work. Enhancement: apply a 30-day default expiration only when a caller omits expiresAt; explicit expiration must take precedence.",
                    List.of(
                            task("baseline-validation", TaskKind.CUSTOM, Set.of(), ImpactLevel.LOW),
                            task("requirements", TaskKind.REQUIREMENTS, Set.of("baseline-validation"), ImpactLevel.LOW),
                            task("architecture", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.LOW),
                            task("implementation", TaskKind.CODE, Set.of("architecture"), ImpactLevel.HIGH),
                            task(
                                    "expiration-regression",
                                    TaskKind.CUSTOM,
                                    Set.of("baseline-validation", "implementation"),
                                    ImpactLevel.LOW),
                            task("review", TaskKind.REVIEW, Set.of("expiration-regression"), ImpactLevel.LOW),
                            task("security", TaskKind.SECURITY, Set.of("implementation"), ImpactLevel.LOW),
                            task(
                                    "documentation",
                                    TaskKind.DOCUMENTATION,
                                    Set.of("review", "security"),
                                    ImpactLevel.LOW))),
            ScenarioId.AMBIGUOUS,
            new ScenarioDefinition(
                    ScenarioId.AMBIGUOUS,
                    "Ambiguous retention requirements",
                    "Pause high-impact retention decisions for human clarification before implementation.",
                    "Shorten links for a campaign and retain them for an appropriate period. The retention period and behavior for existing links are unspecified; ask for clarification before implementation.",
                    List.of(
                            task("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                            task("human-clarification", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.HIGH),
                            task("implementation", TaskKind.CODE, Set.of("human-clarification"), ImpactLevel.MEDIUM),
                            task("validation", TaskKind.TEST, Set.of("implementation"), ImpactLevel.LOW),
                            task(
                                    "documentation",
                                    TaskKind.DOCUMENTATION,
                                    Set.of("validation"),
                                    ImpactLevel.LOW))));

    public List<ScenarioDescriptor> list() {
        return List.of(ScenarioId.values()).stream()
                .map(this::get)
                .map(ScenarioDefinition::descriptor)
                .toList();
    }

    public ScenarioDefinition get(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Scenario ID is required");
        }
        try {
            return get(ScenarioId.valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown scenario: " + id);
        }
    }

    public ScenarioDefinition get(ScenarioId id) {
        ScenarioDefinition definition = scenarios.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown scenario: " + id);
        }
        return definition;
    }

    private static TaskDefinition task(String id, TaskKind kind, Set<String> dependencies, ImpactLevel impact) {
        return new TaskDefinition(id, kind, dependencies, impact);
    }
}
