package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class TesterAgent extends AbstractLlmAgent {

    public TesterAgent(LlmProvider provider) {
        super(provider, "Identify regression checks and report expected validation; do not run commands.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.TESTER;
    }
}
