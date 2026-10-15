package com.schwab.agentic.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyEngineTest {

    @TempDir
    Path sandboxRoot;

    @Test
    void rejectsPathsThatEscapeSandbox() {
        PolicyEngine policyEngine = policyEngine(List.of());

        assertThrows(
                GuardrailViolationException.class,
                () -> policyEngine.evaluate(
                        ArtifactOperation.WRITE_ARTIFACT, "..\\outside.txt", "safe content", null, false));
    }

    @Test
    void blocksSecretLikeContent() {
        PolicyEngine policyEngine = policyEngine(List.of());

        GuardrailResult result = policyEngine.evaluate(
                ArtifactOperation.WRITE_ARTIFACT,
                "docs\\example.md",
                "api_key = 'very-long-secret-value'",
                null,
                false);

        assertEquals(GuardrailDecision.DENY, result.decision());
        org.junit.jupiter.api.Assertions.assertTrue(result.reason().contains("suspected secret"));
    }

    @Test
    void requiresExplicitApprovalForHighImpactFiles() {
        PolicyEngine policyEngine = policyEngine(List.of());

        GuardrailResult pending = policyEngine.evaluate(
                ArtifactOperation.WRITE_ARTIFACT, "pom.xml", "<project/>", null, false);
        GuardrailResult approved = policyEngine.evaluate(
                ArtifactOperation.WRITE_ARTIFACT, "pom.xml", "<project/>", null, true);

        assertEquals(GuardrailDecision.APPROVAL_REQUIRED, pending.decision());
        assertEquals(ImpactLevel.HIGH, pending.impact());
        assertEquals(GuardrailDecision.ALLOW, approved.decision());
    }

    @Test
    void onlyAllowsRegisteredCheckIdentifiers() {
        RegisteredCheck check = new RegisteredCheck() {
            @Override
            public RegisteredCheckId id() {
                return RegisteredCheckId.UNIT_TESTS;
            }

            @Override
            public RegisteredCheckResult run() {
                throw new AssertionError("Policy evaluation must not run checks");
            }
        };
        PolicyEngine policyEngine = policyEngine(List.of(check));

        GuardrailResult allowlisted = policyEngine.evaluate(
                ArtifactOperation.RUN_REGISTERED_CHECK, null, null, RegisteredCheckId.UNIT_TESTS, false);
        GuardrailResult unregistered = policyEngine.evaluate(
                ArtifactOperation.RUN_REGISTERED_CHECK, null, null, RegisteredCheckId.COMPILE, false);

        assertEquals(GuardrailDecision.ALLOW, allowlisted.decision());
        assertEquals(GuardrailDecision.DENY, unregistered.decision());
    }

    private PolicyEngine policyEngine(List<RegisteredCheck> checks) {
        return new PolicyEngine(
                new ArtifactSandbox(sandboxRoot.toString()),
                new SecretScanner(),
                new RiskClassifier(),
                new RegisteredCheckRegistry(checks));
    }
}
