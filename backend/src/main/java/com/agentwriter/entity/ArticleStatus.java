package com.agentwriter.entity;

import java.util.Locale;
import java.util.Set;

public enum ArticleStatus {
    DRAFT("draft"),
    GENERATING("generating"),
    GENERATED("generated"),
    PUBLISHING("publishing"),
    FAILED("failed");

    private final String databaseValue;

    ArticleStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public boolean canTransitionTo(ArticleStatus target) {
        return switch (this) {
            case DRAFT -> target == GENERATING;
            case GENERATING -> Set.of(GENERATED, FAILED).contains(target);
            case GENERATED -> target == PUBLISHING;
            case PUBLISHING -> target == GENERATED;
            case FAILED -> false;
        };
    }

    public static ArticleStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文章状态不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ArticleStatus status : values()) {
            if (status.databaseValue.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知文章状态: " + value);
    }
}
