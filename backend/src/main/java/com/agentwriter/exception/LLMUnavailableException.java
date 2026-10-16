package com.agentwriter.exception;

import java.io.Serial;

public class LLMUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LLMUnavailableException(String message) {
        super(message);
    }
}