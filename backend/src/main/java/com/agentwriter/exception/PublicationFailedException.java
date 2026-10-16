package com.agentwriter.exception;

import java.io.Serial;

public class PublicationFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PublicationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}