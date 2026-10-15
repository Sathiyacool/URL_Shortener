package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class RequirementsAgent extends AbstractLlmAgent {

    public RequirementsAgent(LlmProvider provider) {
        super(provider, "Extract explicit functional requirements, constraints, and unresolved questions.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.REQUIREMENTS;
    }
}
