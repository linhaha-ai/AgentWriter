package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台模板创建/更新请求
 */
public record AdminTemplateRequest(
        @NotBlank String name,
        String description,
        String titleTemplate,
        String outlineTemplate,
        String coverRule,
        Boolean enabled
) {}