package com.agentwriter.exception;

/**
 * 图片生成服务未配置或暂不可用。
 */
public class ImageGenerationUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageGenerationUnavailableException(String message) {
        super(message);
    }

    public ImageGenerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
