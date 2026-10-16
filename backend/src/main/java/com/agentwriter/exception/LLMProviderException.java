package com.agentwriter.exception;

import com.agentwriter.common.CategorizedError;
import com.agentwriter.common.ErrorCategory;

import java.io.Serial;

public class LLMProviderException extends RuntimeException implements CategorizedError {

    @Serial
    private static final long serialVersionUID = 1L;
    private final ErrorCategory category;

    public LLMProviderException(ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public LLMProviderException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}