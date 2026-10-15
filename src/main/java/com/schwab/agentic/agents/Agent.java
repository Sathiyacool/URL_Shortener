package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProviderException;

public interface Agent {

    AgentRole role();

    AgentResult execute(AgentRequest request) throws LlmProviderException;
}
