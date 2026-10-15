package com.schwab.agentic.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schwab.agentic.llm.LlmProvider;
import com.schwab.agentic.llm.LlmRequest;
import com.schwab.agentic.llm.LlmResponse;
import com.schwab.agentic.llm.MockLlmProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentProviderTest {

    @Test
    void mockProviderProducesStableRoleSpecificArtifacts() {
        MockLlmProvider provider = new MockLlmProvider();
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("b", "second result");
        dependencies.put("a", "first result");
        LlmRequest request = new LlmRequest(
                AgentRole.CODER,
                "code-task",
                "Draft a proposal",
                "Add expiration handling",
                dependencies);

        LlmResponse first = provider.generate(request);
        LlmResponse second = provider.generate(request);

        assertEquals(first, second);
        assertEquals(0.95, first.confidence());
        assertEquals(1, first.artifacts().size());
        org.junit.jupiter.api.Assertions.assertTrue(first.artifacts().getFirst().relativePath().endsWith(".md"));
    }

    @Test
    void agentPassesTypedInputsThroughProviderBoundary() throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<LlmRequest>();
        LlmProvider provider = request -> {
            captured.set(request);
            return new LlmResponse("requirements summarized", 0.8, java.util.List.of());
        };
        RequirementsAgent agent = new RequirementsAgent(provider);
        AgentRequest request = new AgentRequest(
                UUID.randomUUID(),
                "requirements",
                "Create a shortener",
                Map.of("source", "Use Spring Boot"));

        AgentResult result = agent.execute(request);

        assertEquals("requirements summarized", result.summary());
        assertEquals(AgentRole.REQUIREMENTS, captured.get().role());
        assertEquals("Create a shortener", captured.get().objective());
        assertEquals(Map.of("source", "Use Spring Boot"), captured.get().dependencyOutputs());
    }

    @Test
    void agentRejectsMissingProviderResponse() {
        LlmProvider provider = request -> null;
        RequirementsAgent agent = new RequirementsAgent(provider);

        assertThrows(
                AgentExecutionException.class,
                () -> agent.execute(new AgentRequest(UUID.randomUUID(), "requirements", "objective", Map.of())));
    }

    @Test
    void agentResultRejectsInvalidConfidenceAndSummary() {
        assertThrows(IllegalArgumentException.class, () -> new AgentResult("valid", Double.NaN, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentResult("", 0.5, null));
    }
}
