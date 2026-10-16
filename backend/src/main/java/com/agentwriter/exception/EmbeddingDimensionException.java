package com.agentwriter.exception;

import java.io.Serial;

public class EmbeddingDimensionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmbeddingDimensionException(String message) {
        super(message);
    }
}