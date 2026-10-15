package com.schwab.agentic.guardrails;

import com.schwab.agentic.orchestration.dag.ImpactLevel;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RiskClassifier {

    public ImpactLevel classify(ArtifactOperation operation, String relativePath) {
        if (operation == ArtifactOperation.DELETE_ARTIFACT) {
            return ImpactLevel.HIGH;
        }
        if (operation == ArtifactOperation.RUN_REGISTERED_CHECK || operation == ArtifactOperation.READ_ARTIFACT) {
            return ImpactLevel.LOW;
        }
        String path = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.equals("pom.xml")
                || path.startsWith("src/main/resources/")
                || path.contains("/api/")
                || path.startsWith("src/main/java/") && path.contains("/api/")) {
            return ImpactLevel.HIGH;
        }
        if (path.startsWith("docs/") || path.endsWith(".md")) {
            return ImpactLevel.LOW;
        }
        return ImpactLevel.MEDIUM;
    }
}
