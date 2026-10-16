package com.agentwriter.service;

import com.agentwriter.config.ImageProperties;
import com.agentwriter.entity.ArticleImage;
import com.agentwriter.exception.ImageGenerationUnavailableException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用本地图片文件存储。
 */
@Service
public class GeneratedImageStorage {

    private final ImageProperties properties;

    public GeneratedImageStorage(ImageProperties properties) {
        this.properties = properties;
    }

    public StoredImage store(Long articleId, Long imageId, ComfyGeneratedImage image) {
        Path root = rootDirectory();
        String extension = extension(image.filename());
        String storageKey = articleId + "/" + imageId + extension;
        Path target = resolve(root, storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, image.bytes());
            return new StoredImage(storageKey, image.contentType());
        } catch (IOException exception) {
            throw new ImageGenerationUnavailableException("无法保存生成图片", exception);
        }
    }

    public Resource load(ArticleImage image) {
        if (image.getStorageKey() == null || image.getStorageKey().isBlank()) {
            throw new IllegalArgumentException("图片文件不存在");
        }
        try {
            UrlResource resource = new UrlResource(resolve(rootDirectory(), image.getStorageKey()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("图片文件不存在");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("图片文件路径无效", exception);
        }
    }

    public byte[] readBytes(ArticleImage image) {
        try {
            return Files.readAllBytes(resolve(rootDirectory(), image.getStorageKey()));
        } catch (IOException exception) {
            throw new ImageGenerationUnavailableException("无法读取生成图片", exception);
        }
    }

    private Path rootDirectory() {
        if (properties.getStorageDirectory() == null || properties.getStorageDirectory().isBlank()) {
            throw new ImageGenerationUnavailableException("未配置图片存储目录");
        }
        return Path.of(properties.getStorageDirectory()).toAbsolutePath().normalize();
    }

    private static Path resolve(Path root, String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("图片文件路径无效");
        }
        return target;
    }

    private static String extension(String filename) {
        if (filename != null && filename.matches(".*\\.(?i:png|jpe?g|webp)$")) {
            return filename.substring(filename.lastIndexOf('.')).toLowerCase();
        }
        return ".png";
    }

    public record StoredImage(String storageKey, String contentType) {
    }
}
