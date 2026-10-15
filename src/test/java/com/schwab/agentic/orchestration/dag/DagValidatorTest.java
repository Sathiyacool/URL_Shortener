package com.schwab.agentic.orchestration.dag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DagValidatorTest {

    private final DagValidator validator = new DagValidator();

    @Test
    void validatesDependenciesAndProducesDeterministicTopologicalOrder() {
        TaskDag dag = validator.validate(List.of(
                task("test", TaskKind.TEST, Set.of("design"), ImpactLevel.LOW),
                task("design", TaskKind.ARCHITECTURE, Set.of("requirements"), ImpactLevel.MEDIUM),
                task("requirements", TaskKind.REQUIREMENTS, Set.of(), ImpactLevel.LOW),
                task("security", TaskKind.SECURITY, Set.of("requirements"), ImpactLevel.HIGH)));

        assertEquals(List.of("requirements", "design", "security", "test"), dag.topologicalOrder());
        assertEquals(Set.of("requirements"), dag.tasksById().get("design").dependencyIds());
    }

    @Test
    void rejectsDuplicateTaskIds() {
        assertThrows(
                DagValidationException.class,
                () -> validator.validate(List.of(
                        task("same", TaskKind.CODE, Set.of(), ImpactLevel.LOW),
                        task("same", TaskKind.TEST, Set.of(), ImpactLevel.LOW))));
    }

    @Test
    void rejectsUnknownDependencies() {
        assertThrows(
                DagValidationException.class,
                () -> validator.validate(List.of(
                        task("code", TaskKind.CODE, Set.of("missing"), ImpactLevel.LOW))));
    }

    @Test
    void rejectsCycles() {
        assertThrows(
                DagValidationException.class,
                () -> validator.validate(List.of(
                        task("first", TaskKind.CODE, Set.of("second"), ImpactLevel.LOW),
                        task("second", TaskKind.TEST, Set.of("first"), ImpactLevel.LOW))));
    }

    @Test
    void rejectsTaskIdsThatCannotBePersisted() {
        assertThrows(
                DagValidationException.class,
                () -> validator.validate(List.of(
                        task("x".repeat(101), TaskKind.CODE, Set.of(), ImpactLevel.LOW))));
    }

    private TaskDefinition task(String id, TaskKind kind, Set<String> dependencies, ImpactLevel impact) {
        return new TaskDefinition(id, kind, dependencies, impact);
    }
}
