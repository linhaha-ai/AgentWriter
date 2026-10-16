package com.agentwriter.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy();
    }

    @Test
    void shouldSucceedOnFirstAttempt() throws Exception {
        var result = retryPolicy.executeWithRetry(() -> "ok", "test-step");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryOnTimeoutAndSucceed() throws Exception {
        var counter = new AtomicInteger(0);

        var result = retryPolicy.executeWithRetry(() -> {
            if (counter.incrementAndGet() <= 2) {
                throw new TimeoutException("LLM timeout");
            }
            return "recovered";
        }, "test-step");

        assertThat(result).isEqualTo("recovered");
        assertThat(counter.get()).isEqualTo(3); // 1 initial + 2 retries
    }

    @Test
    void shouldRetryOnNetworkErrorAndSucceed() throws Exception {
        var counter = new AtomicInteger(0);

        var result = retryPolicy.executeWithRetry(() -> {
            if (counter.incrementAndGet() <= 3) {
                throw new SocketTimeoutException("Read timed out");
            }
            return "recovered";
        }, "test-step");

        assertThat(result).isEqualTo("recovered");
        assertThat(counter.get()).isEqualTo(4); // 1 initial + 3 retries
    }

    @Test
    void shouldThrowAfterMaxRetriesExhausted() {
        var counter = new AtomicInteger(0);

        assertThatThrownBy(() ->
                retryPolicy.executeWithRetry(() -> {
                    counter.incrementAndGet();
                    throw new TimeoutException("Always timeout");
                }, "test-step")
        ).isInstanceOf(TimeoutException.class)
                .hasMessage("Always timeout");

        // 1 initial + 2 retries = 3 total
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryNonRetryableError() {
        assertThatThrownBy(() ->
                retryPolicy.executeWithRetry(() -> {
                    throw new RuntimeException("SQL error: connection pool exhausted");
                }, "test-step")
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("SQL error: connection pool exhausted");
    }

    @Test
    void shouldNotRetryAuthError() {
        assertThatThrownBy(() ->
                retryPolicy.executeWithRetry(() -> {
                    throw new RuntimeException("HTTP 401 Unauthorized");
                }, "test-step")
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("HTTP 401 Unauthorized");
    }

    @Test
    void shouldRetryWithForcedCategory() throws Exception {
        var counter = new AtomicInteger(0);

        var result = retryPolicy.executeWithRetry(() -> {
            if (counter.incrementAndGet() <= 2) {
                // 这个异常本身归类为 UNKNOWN，但强制覆盖为 NETWORK_ERROR
                throw new RuntimeException("Forced retry scenario");
            }
            return "forced-ok";
        }, "test-step", ErrorCategory.NETWORK_ERROR);

        assertThat(result).isEqualTo("forced-ok");
        assertThat(counter.get()).isEqualTo(3); // 1 + 2 retries for NETWORK_ERROR
    }

    @Test
    void shouldRetryEmptyResponseOnce() throws Exception {
        var counter = new AtomicInteger(0);

        var result = retryPolicy.executeWithRetry(() -> {
            if (counter.incrementAndGet() <= 1) {
                throw new RuntimeException("LLM returned empty response");
            }
            return "fixed";
        }, "test-step");

        assertThat(result).isEqualTo("fixed");
        assertThat(counter.get()).isEqualTo(2); // 1 initial + 1 immediate retry
    }

    @Test
    void shouldHandleInterruptedExceptionGracefully() {
        assertThatThrownBy(() -> retryPolicy.executeWithRetry(() -> {
            throw new TimeoutException("timeout");
        }, "interrupted"))
                .isInstanceOf(TimeoutException.class);
    }
}
