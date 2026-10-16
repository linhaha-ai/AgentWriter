package com.agentwriter.exception;

import java.io.Serial;

public class EmbeddingUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmbeddingUnavailableException(String message) {
        super(message);
    }
}