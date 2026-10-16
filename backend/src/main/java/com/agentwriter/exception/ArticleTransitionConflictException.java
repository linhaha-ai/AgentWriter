package com.agentwriter.exception;

import com.agentwriter.entity.ArticleStatus;

import java.io.Serial;

public class ArticleTransitionConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ArticleTransitionConflictException(Long id, ArticleStatus expected, ArticleStatus target) {
        super("文章状态转换冲突: id=" + id
                + ", expected=" + expected.databaseValue()
                + ", target=" + target.databaseValue());
    }
}