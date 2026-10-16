package com.agentwriter.exception;

import java.io.Serial;

public class ArticleNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ArticleNotFoundException(Long id) {
        super("文章不存在: id=" + id);
    }
}