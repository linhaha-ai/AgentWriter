package com.agentwriter.service;

/**
 * ComfyUI 返回的单张图片二进制内容。
 */
public record ComfyGeneratedImage(String promptId, String filename, byte[] bytes, String contentType) {
}
