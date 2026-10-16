package com.agentwriter.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void runtimeErrorDoesNotExposeInternalDetails() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleRuntime(new RuntimeException("token=synthetic-secret"));

        assertThat(response.error()).isEqualTo("服务器内部错误");
        assertThat(response.error()).doesNotContain("synthetic-secret");
    }

    @Test
    void uploadSizeErrorUsesClearClientMessage() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertThat(response.error()).isEqualTo("上传图片超过大小限制");
    }
}
