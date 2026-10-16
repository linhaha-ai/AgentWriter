package com.agentwriter.controller;

import com.agentwriter.entity.ArticleImage;
import com.agentwriter.service.ArticleImageService;
import com.agentwriter.service.GeneratedImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 提供应用托管的生成图片文件。
 */
@RestController
@RequestMapping("/api/article-images")
public class ArticleImageController {

    private final ArticleImageService articleImageService;
    private final GeneratedImageStorage imageStorage;

    public ArticleImageController(ArticleImageService articleImageService, GeneratedImageStorage imageStorage) {
        this.articleImageService = articleImageService;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        ArticleImage image = articleImageService.requireReadyImage(id);
        Resource resource = imageStorage.load(image);
        MediaType contentType = image.getContentType() == null || image.getContentType().isBlank()
                ? MediaType.IMAGE_PNG
                : MediaType.parseMediaType(image.getContentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}
