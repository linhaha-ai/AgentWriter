package com.agentwriter.agent.impl;

import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import com.agentwriter.exception.PublicationFailedException;
import com.agentwriter.repository.ArticleImageRepository;
import com.agentwriter.service.GeneratedImageStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将应用托管的生成图片上传为微信公众号素材。
 */
@Component
public class WeChatImageMaterialService {

    private static final String CONTENT_IMAGE_UPLOAD_PATH = "/cgi-bin/media/uploadimg";
    private static final String THUMB_UPLOAD_PATH = "/cgi-bin/material/add_material";

    private final ArticleImageRepository articleImageRepository;
    private final GeneratedImageStorage imageStorage;
    private final ObjectMapper objectMapper;

    public WeChatImageMaterialService(
            ArticleImageRepository articleImageRepository,
            GeneratedImageStorage imageStorage,
            ObjectMapper objectMapper
    ) {
        this.articleImageRepository = articleImageRepository;
        this.imageStorage = imageStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PreparedWechatArticle prepare(
            Article article,
            String accessToken,
            String apiBaseUrl,
            String defaultThumbMediaId,
            WebClient webClient
    ) {
        List<ArticleImage> images = articleImageRepository.findByArticleIdAndInsertedTrueOrderByCreatedAtAsc(article.getId());
        Map<String, String> contentUrls = new HashMap<>();
        for (ArticleImage image : images) {
            if (image.getStatus() != ArticleImageStatus.READY) {
                throw failure("文章包含未完成的生成图片");
            }
            String url = image.getWechatContentUrl();
            if (url == null || url.isBlank()) {
                url = uploadContentImage(image, accessToken, apiBaseUrl, webClient);
                image.setWechatContentUrl(url);
                articleImageRepository.save(image);
            }
            contentUrls.put(contentPath(image.getId()), url);
        }

        String thumbMediaId = articleImageRepository.findFirstByArticleIdAndRoleAndStatusOrderByCreatedAtDesc(
                        article.getId(), ArticleImageRole.COVER, ArticleImageStatus.READY)
                .map(image -> uploadThumbIfNeeded(image, accessToken, apiBaseUrl, webClient))
                .orElse(defaultThumbMediaId);
        String content = WeChatContentRenderer.render(article, source -> contentUrls.getOrDefault(source, source));
        return new PreparedWechatArticle(content, thumbMediaId);
    }

    private String uploadThumbIfNeeded(ArticleImage image, String accessToken, String apiBaseUrl, WebClient webClient) {
        if (image.getWechatThumbMediaId() != null && !image.getWechatThumbMediaId().isBlank()) {
            return image.getWechatThumbMediaId();
        }
        JsonNode response = upload(image, accessToken, apiBaseUrl, THUMB_UPLOAD_PATH, "type=thumb", webClient);
        String mediaId = requiredText(response, "media_id", "微信公众号未返回封面素材 ID");
        image.setWechatThumbMediaId(mediaId);
        articleImageRepository.save(image);
        return mediaId;
    }

    private String uploadContentImage(ArticleImage image, String accessToken, String apiBaseUrl, WebClient webClient) {
        JsonNode response = upload(image, accessToken, apiBaseUrl, CONTENT_IMAGE_UPLOAD_PATH, null, webClient);
        return requiredText(response, "url", "微信公众号未返回正文图片 URL");
    }

    private JsonNode upload(
            ArticleImage image,
            String accessToken,
            String apiBaseUrl,
            String path,
            String query,
            WebClient webClient
    ) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        MediaType contentType = image.getContentType() == null || image.getContentType().isBlank()
                ? MediaType.IMAGE_PNG
                : MediaType.parseMediaType(image.getContentType());
        body.part("media", imageStorage.readBytes(image))
                .filename("article-image-" + image.getId() + extension(contentType))
                .contentType(contentType);
        String uri = apiBaseUrl + path + "?access_token={token}" + (query == null ? "" : "&" + query);
        try {
            JsonNode response = webClient.post()
                    .uri(uri, accessToken)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::readJson)
                    .block(Duration.ofSeconds(30));
            checkError(response);
            return response;
        } catch (PublicationFailedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PublicationFailedException("微信公众号图片上传失败", exception);
        }
    }

    private JsonNode readJson(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new PublicationFailedException("微信公众号图片上传响应无效", exception);
        }
    }

    private static void checkError(JsonNode response) {
        int errorCode = response == null ? -1 : response.path("errcode").asInt(0);
        if (errorCode != 0) {
            throw failure("微信公众号图片上传失败: " + response.path("errmsg").asText("未知错误"));
        }
    }

    private static String requiredText(JsonNode response, String field, String message) {
        String value = response == null ? null : response.path(field).asText();
        if (value == null || value.isBlank()) {
            throw failure(message);
        }
        return value;
    }

    private static String contentPath(Long imageId) {
        return "/api/article-images/" + imageId + "/content";
    }

    private static PublicationFailedException failure(String message) {
        return new PublicationFailedException(message, new IllegalStateException(message));
    }

    private static String extension(MediaType contentType) {
        if (MediaType.IMAGE_JPEG.includes(contentType)) {
            return ".jpg";
        }
        if ("image".equals(contentType.getType()) && "webp".equals(contentType.getSubtype())) {
            return ".webp";
        }
        return ".png";
    }

    public record PreparedWechatArticle(String content, String thumbMediaId) {
    }
}
