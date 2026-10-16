package com.agentwriter.exception;

import java.io.Serial;

public class EmbeddingValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmbeddingValidationException(String message) {
        super(message);
    }
}