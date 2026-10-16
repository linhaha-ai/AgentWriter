package com.agentwriter.exception;

import java.io.Serial;

public class TopicNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TopicNotFoundException(Long id) {
        super("话题不存在: id=" + id);
    }
}