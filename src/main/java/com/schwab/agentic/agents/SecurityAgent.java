package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class SecurityAgent extends AbstractLlmAgent {

    public SecurityAgent(LlmProvider provider) {
        super(provider, "Identify security and compliance risks and recommend mitigations.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.SECURITY;
    }
}
