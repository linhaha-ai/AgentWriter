package com.agentwriter.common;

/**
 * 错误分类 - 区分可重试与不可重试的错误类型。
 *
 * <p>用于 WorkflowEngine 决策：遇到 retryable 错误时自动重试，
 * 遇到 nonRetryable 错误时立即终止工作流并记录详细原因。</p>
 */
public enum ErrorCategory {

    /** LLM API 调用超时 - 可重试 */
    LLM_TIMEOUT(true),

    /** 网络错误 - 可重试 */
    NETWORK_ERROR(true),

    /** API 限流 - 可重试（等待后） */
    RATE_LIMITED(true),

    /** LLM 返回空或无效内容 - 可重试一次 */
    LLM_EMPTY_RESPONSE(true),

    /** JSON 解析失败 - 可重试一次（重新约束格式） */
    JSON_PARSE_ERROR(true),

    /** 无效提示词或输入 - 不可重试 */
    INVALID_INPUT(false),

    /** LLM 认证失败（API Key 错误）- 不可重试 */
    AUTH_ERROR(false),

    /** 内容质量不达标 - 不可重试，需人工介入 */
    QUALITY_FAILURE(false),

    /** 数据库错误 - 不可重试 */
    DATABASE_ERROR(false),

    /** 未知错误 - 保守处理，不可重试 */
    UNKNOWN(false);

    private final boolean retryable;

    ErrorCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 根据异常消息推断错误分类。
     */
    public static ErrorCategory classify(Throwable e) {
        if (e == null) return UNKNOWN;
        if (e instanceof CategorizedError categorizedError) {
            return categorizedError.category();
        }

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // 网络层超时（SocketTimeoutException）→ NETWORK_ERROR
        if (e instanceof java.net.SocketTimeoutException
                || msg.contains("read timed out")
                || msg.contains("connect timed out")) {
            return NETWORK_ERROR;
        }
        // LLM API 超时
        if (e instanceof java.util.concurrent.TimeoutException
                || msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("超时")) {
            return LLM_TIMEOUT;
        }
        if (msg.contains("rate limit") || msg.contains("429") || msg.contains("too many requests")) {
            return RATE_LIMITED;
        }
        if (msg.contains("auth") || msg.contains("401") || msg.contains("403")
                || msg.contains("api key") || msg.contains("unauthorized")) {
            return AUTH_ERROR;
        }
        if (msg.contains("json") || msg.contains("parse") || msg.contains("unexpected token")) {
            return JSON_PARSE_ERROR;
        }
        if (msg.contains("empty") || msg.contains("null") || msg.contains("blank")) {
            return LLM_EMPTY_RESPONSE;
        }
        if (msg.contains("connection") || msg.contains("reset") || msg.contains("refused")
                || msg.contains("dns") || msg.contains("unknownhost")) {
            return NETWORK_ERROR;
        }
        if (msg.contains("sql") || msg.contains("database") || msg.contains("jdbc")) {
            return DATABASE_ERROR;
        }
        return UNKNOWN;
    }
}
