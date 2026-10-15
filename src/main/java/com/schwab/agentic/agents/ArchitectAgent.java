package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class ArchitectAgent extends AbstractLlmAgent {

    public ArchitectAgent(LlmProvider provider) {
        super(provider, "Propose modular Java design choices and dependency boundaries; do not issue commands.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.ARCHITECT;
    }
}
