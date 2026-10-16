package com.agentwriter.common;

import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.exception.ImageGenerationUnavailableException;
import com.agentwriter.exception.TopicNotFoundException;
import com.agentwriter.exception.PublicationFailedException;
import com.agentwriter.exception.SystemConfigValidationException;
import com.agentwriter.exception.PublisherUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        var message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return ApiResponse.error(message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ApiResponse.error("上传图片超过大小限制");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResponse.error("请求参数无效");
    }

    @ExceptionHandler(SystemConfigValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleSystemConfigValidation(SystemConfigValidationException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleArticleNotFound(ArticleNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(TopicNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleTopicNotFound(TopicNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(ArticleTransitionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleArticleTransitionConflict(ArticleTransitionConflictException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler({PublisherUnavailableException.class, PublicationFailedException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handlePublicationFailure(RuntimeException ex) {
        return ApiResponse.error("发布服务暂不可用");
    }

    @ExceptionHandler(ImageGenerationUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleImageGenerationUnavailable(ImageGenerationUnavailableException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntime(RuntimeException ex) {
        log.error("服务发生未处理异常: type={}", ex.getClass().getName());
        return ApiResponse.error("服务器内部错误");
    }
}
