package com.schwab.agentic.guardrails;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import org.springframework.stereotype.Service;

@Service
public class PolicyEngine {

    private final ArtifactSandbox artifactSandbox;
    private final SecretScanner secretScanner;
    private final RiskClassifier riskClassifier;
    private final RegisteredCheckRegistry checkRegistry;

    public PolicyEngine(
            ArtifactSandbox artifactSandbox,
            SecretScanner secretScanner,
            RiskClassifier riskClassifier,
            RegisteredCheckRegistry checkRegistry) {
        this.artifactSandbox = artifactSandbox;
        this.secretScanner = secretScanner;
        this.riskClassifier = riskClassifier;
        this.checkRegistry = checkRegistry;
    }

    public GuardrailResult evaluate(
            ArtifactOperation operation,
            String artifactPath,
            String content,
            RegisteredCheckId checkId,
            boolean approvalGranted) {
        if (operation == null) {
            return new GuardrailResult(GuardrailDecision.DENY, ImpactLevel.HIGH, "Operation is required");
        }
        if (operation == ArtifactOperation.RUN_REGISTERED_CHECK) {
            if (artifactPath != null || content != null || !checkRegistry.isRegistered(checkId)) {
                return new GuardrailResult(
                        GuardrailDecision.DENY,
                        ImpactLevel.LOW,
                        "Only registered check IDs may be run; command text is not accepted");
            }
            return new GuardrailResult(GuardrailDecision.ALLOW, ImpactLevel.LOW, "Registered check is allowlisted");
        }
        if (checkId != null) {
            return new GuardrailResult(
                    GuardrailDecision.DENY, ImpactLevel.HIGH, "Check IDs apply only to RUN_REGISTERED_CHECK");
        }
        if ((operation == ArtifactOperation.WRITE_ARTIFACT && content == null)
                || (operation != ArtifactOperation.WRITE_ARTIFACT && content != null)) {
            return new GuardrailResult(
                    GuardrailDecision.DENY, ImpactLevel.HIGH, "Operation payload does not match its type");
        }

        artifactSandbox.resolve(artifactPath);
        ImpactLevel impact = riskClassifier.classify(operation, artifactPath);
        if (operation == ArtifactOperation.WRITE_ARTIFACT) {
            var finding = secretScanner.findSecret(content);
            if (finding.isPresent()) {
                return new GuardrailResult(
                        GuardrailDecision.DENY,
                        impact,
                        "Artifact contains a suspected secret (" + finding.get() + ")");
            }
        }
        if ((impact == ImpactLevel.HIGH || operation == ArtifactOperation.DELETE_ARTIFACT) && !approvalGranted) {
            return new GuardrailResult(
                    GuardrailDecision.APPROVAL_REQUIRED,
                    impact,
                    "Explicit human approval is required for this operation");
        }
        return new GuardrailResult(GuardrailDecision.ALLOW, impact, "Operation passed policy checks");
    }
}
