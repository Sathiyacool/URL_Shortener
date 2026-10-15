package com.schwab.agentic.llm;

public class LlmProviderException extends Exception {

    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
