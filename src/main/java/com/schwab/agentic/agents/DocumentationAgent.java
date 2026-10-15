package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class DocumentationAgent extends AbstractLlmAgent {

    public DocumentationAgent(LlmProvider provider) {
        super(provider, "Prepare concise user and architecture documentation as a sandbox artifact.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.DOCUMENTATION;
    }
}
