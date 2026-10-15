package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class ReviewerAgent extends AbstractLlmAgent {

    public ReviewerAgent(LlmProvider provider) {
        super(provider, "Review upstream decisions and artifacts for correctness and maintainability.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.REVIEWER;
    }
}
