package com.schwab.agentic.llm;

public interface LlmProvider {

    LlmResponse generate(LlmRequest request) throws LlmProviderException;
}
