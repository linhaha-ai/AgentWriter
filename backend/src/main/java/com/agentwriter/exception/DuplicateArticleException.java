package com.agentwriter.exception;

import java.io.Serial;

/**
 * 已存在相同标题的文章，跳过生成。
 */
public class DuplicateArticleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateArticleException(String title, Long existingId, String existingStatus) {
        super("已存在相同标题的文章（ID=%d，状态=%s），已跳过生成。如需重新生成，请先修改或删除原文章标题。".formatted(
                existingId, existingStatus));
    }
}
