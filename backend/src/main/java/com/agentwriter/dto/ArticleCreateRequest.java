package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;

public record ArticleCreateRequest(
        @NotBlank String title,
        String contentMd,
        String source,
        String coverImage
) {}
