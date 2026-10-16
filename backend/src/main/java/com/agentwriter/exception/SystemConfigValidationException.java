package com.agentwriter.exception;

import java.io.Serial;

public class SystemConfigValidationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SystemConfigValidationException(String message) {
        super(message);
    }

    public SystemConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}