package com.schwab.agentic.guardrails;

import com.schwab.agentic.orchestration.dag.ImpactLevel;

public record GuardrailResult(GuardrailDecision decision, ImpactLevel impact, String reason) {
}
