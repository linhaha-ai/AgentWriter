package com.agentwriter.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 响应解析工具。
 *
 * <p>当前 writer/review/seo 提示词已要求模型直接输出 Markdown 正文（不再输出 JSON 信封）。
 * 但模型偶发仍会返回 {@code ```json {"success":true,"data":{...}} ```} 之类的旧格式信封，
 * 因此保留此解析器做兼容：先用 {@link #looksLikeJsonEnvelope} 判断响应是否疑似信封——
 * 疑似信封时调用方回退使用输入原文（避免把整段信封存为正文），
 * 否则直接把响应文本当作正文。{@link #extractDataField} 供旧链路/历史数据解析使用。</p>
 */
public final class LlmResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LlmResponseParser() {
    }

    /**
     * 去除 Markdown 代码围栏（```json ... ``` 或 ``` ... ```）。
     */
    public static String stripCodeFence(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    /**
     * 判断响应是否疑似 JSON 信封：以 {@code {} 开头或带 Markdown 代码围栏。
     * 用于解析失败时决定降级策略——疑似信封则回退输入内容，否则直接采用模型输出正文。
     */
    public static boolean looksLikeJsonEnvelope(String response) {
        if (response == null) {
            return false;
        }
        String trimmed = response.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("```");
    }

    /**
     * 从 {@code data} 节点中依次尝试指定字段，返回字段文本。
     *
     * <p>文本节点返回其字符串值；对象/数组节点序列化为 JSON 字符串。
     * 任意环节失败或字段均不存在时返回 {@code null}，由调用方决定回退策略。</p>
     *
     * @param response   模型原始响应
     * @param fieldNames 按优先级尝试的字段名
     */
    public static String extractDataField(String response, String... fieldNames) {
        if (response == null || response.isBlank() || fieldNames == null) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(stripCodeFence(response));
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            for (String fieldName : fieldNames) {
                JsonNode node = data.get(fieldName);
                if (node != null && !node.isNull()) {
                    if (node.isTextual()) {
                        return node.asText();
                    }
                    return OBJECT_MAPPER.writeValueAsString(node);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
