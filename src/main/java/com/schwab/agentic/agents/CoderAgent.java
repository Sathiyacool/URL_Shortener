package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import org.springframework.stereotype.Component;

@Component
public class CoderAgent extends AbstractLlmAgent {

    public CoderAgent(LlmProvider provider) {
        super(provider, "Draft a proposed code change as a sandbox artifact; never apply it to source directly.");
    }

    @Override
    public AgentRole role() {
        return AgentRole.CODER;
    }
}
