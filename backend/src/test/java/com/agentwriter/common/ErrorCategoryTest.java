package com.agentwriter.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoryTest {

    @Test
    void shouldClassifyTimeoutException() {
        var ex = new TimeoutException("LLM API call timed out");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.LLM_TIMEOUT);
    }

    @Test
    void shouldClassifyNetworkTimeoutAsConnectException() {
        var ex = new SocketTimeoutException("Read timed out");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test
    void shouldClassifyNetworkErrorAsConnectException() {
        var ex = new ConnectException("Connection refused");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test
    void shouldClassifyIOException() {
        var ex = new IOException("Connection reset");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test
    void shouldClassifyRateLimitedByMessage() {
        var ex = new RuntimeException("Rate limit exceeded for api.openai.com");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void shouldClassify429AsRateLimited() {
        var ex = new RuntimeException("HTTP 429 Too Many Requests");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void shouldClassifyEmptyResponse() {
        var ex = new RuntimeException("LLM returned empty response");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.LLM_EMPTY_RESPONSE);
    }

    @Test
    void shouldClassifyJsonParseError() {
        var ex = new RuntimeException("Failed to parse LLM response JSON");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.JSON_PARSE_ERROR);
    }

    @Test
    void shouldClassifyAuthError() {
        var ex = new RuntimeException("HTTP 401 Unauthorized: invalid API key");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.AUTH_ERROR);
    }

    @Test
    void shouldClassify403AsAuthError() {
        var ex = new RuntimeException("HTTP 403 Forbidden");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.AUTH_ERROR);
    }

    @Test
    void shouldClassifyDatabaseError() {
        var ex = new RuntimeException("SQLException: Table 'agentwriter.article' doesn't exist");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.DATABASE_ERROR);
    }

    @Test
    void shouldClassifyUnknownByDefault() {
        var ex = new RuntimeException("Something weird happened");
        assertThat(ErrorCategory.classify(ex)).isEqualTo(ErrorCategory.UNKNOWN);
    }

    @Test
    void retryableCategoriesShouldReturnTrue() {
        assertThat(ErrorCategory.LLM_TIMEOUT.isRetryable()).isTrue();
        assertThat(ErrorCategory.NETWORK_ERROR.isRetryable()).isTrue();
        assertThat(ErrorCategory.RATE_LIMITED.isRetryable()).isTrue();
        assertThat(ErrorCategory.LLM_EMPTY_RESPONSE.isRetryable()).isTrue();
        assertThat(ErrorCategory.JSON_PARSE_ERROR.isRetryable()).isTrue();
    }

    @Test
    void nonRetryableCategoriesShouldReturnFalse() {
        assertThat(ErrorCategory.INVALID_INPUT.isRetryable()).isFalse();
        assertThat(ErrorCategory.AUTH_ERROR.isRetryable()).isFalse();
        assertThat(ErrorCategory.QUALITY_FAILURE.isRetryable()).isFalse();
        assertThat(ErrorCategory.DATABASE_ERROR.isRetryable()).isFalse();
        assertThat(ErrorCategory.UNKNOWN.isRetryable()).isFalse();
    }
}
