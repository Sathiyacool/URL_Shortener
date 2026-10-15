package com.schwab.agentic.agents;

import com.schwab.agentic.llm.LlmProvider;
import com.schwab.agentic.llm.LlmRequest;
import com.schwab.agentic.llm.LlmResponse;
import com.schwab.agentic.llm.LlmProviderException;

public abstract class AbstractLlmAgent implements Agent {

    private final LlmProvider llmProvider;
    private final String instruction;

    protected AbstractLlmAgent(LlmProvider llmProvider, String instruction) {
        this.llmProvider = llmProvider;
        this.instruction = instruction;
    }

    @Override
    public AgentResult execute(AgentRequest request) throws LlmProviderException {
        if (request == null) {
            throw new IllegalArgumentException("Agent request is required");
        }
        LlmResponse response = llmProvider.generate(new LlmRequest(
                role(),
                request.taskId(),
                instruction,
                request.objective(),
                request.dependencyOutputs()));
        if (response == null) {
            throw new AgentExecutionException("LLM provider returned no response for " + role());
        }
        try {
            return new AgentResult(response.summary(), response.confidence(), response.artifacts());
        } catch (IllegalArgumentException exception) {
            throw new AgentExecutionException("LLM provider returned an invalid response for " + role(), exception);
        }
    }
}
