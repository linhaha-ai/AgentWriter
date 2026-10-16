package com.agentwriter.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 重试策略 - 根据 ErrorCategory 执行可配置的退避重试。
 *
 * <p>与 AGENTS.md §10.2 重试策略表保持一致：</p>
 * <pre>
 * LLM_TIMEOUT      → 2 次，间隔 5s
 * NETWORK_ERROR    → 3 次，指数退避 2s→4s→8s
 * RATE_LIMITED     → 1 次，等待 30s
 * LLM_EMPTY_RESPONSE → 1 次，立即
 * JSON_PARSE_ERROR  → 1 次，立即
 * 其余              → 0 次
 * </pre>
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    /**
     * 执行带重试的操作。
     *
     * @param action   要执行的操作
     * @param stepName 步骤名称（用于日志）
     * @param <T>      返回类型
     * @return 操作结果
     * @throws Exception 所有重试耗尽后抛出原始异常
     */
    public <T> T executeWithRetry(SupplierWithException<T> action, String stepName) throws Exception {
        return executeWithRetry(action, stepName, null);
    }

    /**
     * 执行带重试的操作，附带可选的错误分类覆盖。
     */
    public <T> T executeWithRetry(SupplierWithException<T> action, String stepName,
                                  ErrorCategory forcedCategory) throws Exception {
        int maxRetries = 0;
        Duration delay = Duration.ZERO;

        // 如果没有强制分类，等异常发生后再分类
        // 先执行第一次
        try {
            return action.get();
        } catch (Exception e) {
            ErrorCategory category = forcedCategory != null ? forcedCategory : ErrorCategory.classify(e);
            maxRetries = getMaxRetries(category);
            delay = getDelay(category);

            if (maxRetries <= 0 || !category.isRetryable()) {
                log.warn("步骤 [{}] 遇到不可重试错误 [{}]: {}", stepName, category, e.getMessage());
                throw e;
            }

            log.warn("步骤 [{}] 失败 [{}]，将在 {} 后进行 {} 次重试: {}",
                    stepName, category, delay, maxRetries, e.getMessage());
        }

        // 重试循环
        Throwable lastException;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                if (!delay.isZero()) {
                    Thread.sleep(delay.toMillis());
                }
                return action.get();
            } catch (Exception e) {
                lastException = e;
                ErrorCategory category = forcedCategory != null ? forcedCategory : ErrorCategory.classify(e);

                if (attempt >= maxRetries || !category.isRetryable()) {
                    log.warn("步骤 [{}] 重试 {}/{} 耗尽，最终错误 [{}]: {}",
                            stepName, attempt, maxRetries, category, e.getMessage());
                    // 抛出原始异常
                    throw e;
                }

                // 指数退避
                delay = delay.multipliedBy(2);
                log.warn("步骤 [{}] 重试 {}/{} 失败 [{}]，{} 后再次重试: {}",
                        stepName, attempt, maxRetries, category, delay, e.getMessage());
            }
        }
    }

    /**
     * 根据 AGENTS.md §10.2 返回最大重试次数。
     */
    private int getMaxRetries(ErrorCategory category) {
        return switch (category) {
            case LLM_TIMEOUT -> 2;
            case NETWORK_ERROR -> 3;
            case RATE_LIMITED -> 1;
            case LLM_EMPTY_RESPONSE -> 1;
            case JSON_PARSE_ERROR -> 1;
            default -> 0;
        };
    }

    /**
     * 根据 AGENTS.md §10.2 返回首次重试间隔。
     */
    private Duration getDelay(ErrorCategory category) {
        return switch (category) {
            case LLM_TIMEOUT -> Duration.ofSeconds(5);
            case NETWORK_ERROR -> Duration.ofSeconds(2);   // 指数退避基础值
            case RATE_LIMITED -> Duration.ofSeconds(30);
            case LLM_EMPTY_RESPONSE -> Duration.ZERO;
            case JSON_PARSE_ERROR -> Duration.ZERO;
            default -> Duration.ZERO;
        };
    }

    /**
     * 允许抛出检查型异常的函数式接口。
     */
    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
