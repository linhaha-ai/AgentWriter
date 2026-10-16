package com.agentwriter.exception;

import java.io.Serial;

public class LLMConfigurationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LLMConfigurationException(String message) {
        super(message);
    }

    public LLMConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}