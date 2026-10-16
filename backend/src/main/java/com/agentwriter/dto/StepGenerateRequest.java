package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 分步生成请求 - 用户选择话题、填写标题并选择模板和风格后提交触发异步生成
 */
public record StepGenerateRequest(
        @NotEmpty List<@NotNull @Positive Long> topicIds,
        @NotBlank @Size(max = 500) String title,
        @Positive Long templateId,
        @NotNull @Positive Long styleId
) {}
