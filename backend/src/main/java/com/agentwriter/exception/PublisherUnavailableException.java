package com.agentwriter.exception;

import java.io.Serial;

public class PublisherUnavailableException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PublisherUnavailableException(String message) {
        super(message);
    }
}