package com.agentwriter.agent.impl;

import com.agentwriter.agent.Publisher;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.Article;
import com.agentwriter.exception.PublicationFailedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信公众号发布器 - 将文章保存到微信公众号草稿箱。
 *
 * <p>实现流程: 获取 access_token → 创建草稿。
 * 公众号接口不支持一键发布，保存成功后需前往公众平台手动发布。</p>
 *
 * <p>启用方式: application.yml 中设置:</p>
 * <pre>
 * agentwriter:
 *   publisher:
 *     wechat:
 *       enabled: true
 *       app-id: ${WECHAT_APP_ID}
 *       app-secret: ${WECHAT_APP_SECRET}
 * </pre>
 *
 * <p>API 参考: <a href="https://developers.weixin.qq.com/doc/subscription/api/">...</a></p>
 */
public class WeChatPublisher implements Publisher {

    private static final Logger log = LoggerFactory.getLogger(WeChatPublisher.class);

    private static final String TOKEN_PATH = "/cgi-bin/token";

    private static final String DRAFT_ADD_PATH = "/cgi-bin/draft/add";

    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    private final String appId;

    private final String appSecret;

    private final String defaultAuthor;

    private final String thumbMediaId;

    private final String apiBaseUrl;

    private final WeChatImageMaterialService imageMaterialService;

    /** 缓存的 access_token 及过期时间 */
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    private final Object tokenRefreshLock = new Object();

    public WeChatPublisher(
            String appId,
            String appSecret,
            String defaultAuthor,
            String thumbMediaId,
            String apiBaseUrl,
            WebClient webClient,
            ObjectMapper objectMapper) {
        this(appId, appSecret, defaultAuthor, thumbMediaId, apiBaseUrl, webClient, objectMapper, null);
    }

    public WeChatPublisher(
            String appId,
            String appSecret,
            String defaultAuthor,
            String thumbMediaId,
            String apiBaseUrl,
            WebClient webClient,
            ObjectMapper objectMapper,
            WeChatImageMaterialService imageMaterialService) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.defaultAuthor = defaultAuthor;
        this.thumbMediaId = thumbMediaId;
        this.apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl.replaceAll("/$", "");
        this.objectMapper = objectMapper;
        this.webClient = webClient;
        this.imageMaterialService = imageMaterialService;

        log.info("初始化 WeChatPublisher: appId={}, author={}", appId, defaultAuthor);
    }

    @Override
    public PublishResult publish(Article article) {
        ensureConfigured();
        log.info("微信公众号存草稿开始: articleId={}, title={}", article.getId(), article.getTitle());

        try {
            // 1. 获取 access_token
            String accessToken = getAccessToken();

            // 2. 创建草稿
            WeChatImageMaterialService.PreparedWechatArticle prepared = imageMaterialService == null
                    ? new WeChatImageMaterialService.PreparedWechatArticle(
                    WeChatContentRenderer.render(article), thumbMediaId)
                    : imageMaterialService.prepare(article, accessToken, apiBaseUrl, thumbMediaId, webClient);
            String mediaId = createDraft(accessToken, article, prepared.content(), prepared.thumbMediaId());
            log.info("草稿创建成功: articleId={}, mediaId={}", article.getId(), mediaId);

            // 3. 公众号接口不支持一键发布，草稿保存成功即提交成功，需前往公众平台手动发布
            log.info("公众号草稿已保存，请前往公众平台手动发布: articleId={}, mediaId={}",
                    article.getId(), mediaId);

            return new PublishResult(
                    "wechat",
                    null,
                    LocalDateTime.now(),
                    article.getId(),
                    prepared.thumbMediaId() != null && !prepared.thumbMediaId().isBlank() ? prepared.thumbMediaId() : null
            );

        } catch (PublisherUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("微信公众号草稿保存失败: articleId={}, type={}", article.getId(), exception.getClass().getSimpleName());
            throw new PublicationFailedException("微信公众号草稿保存失败", exception);
        }
    }

    private void ensureConfigured() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new PublisherUnavailableException("微信公众号发布器缺少环境凭据配置");
        }
    }

    /**
     * 获取 access_token（带缓存，自动刷新过期 token）。
     *
     * <p>WeChat API: GET /cgi-bin/token?grant_type=client_credential&amp;appid=APPID&amp;secret=APPSECRET</p>
     *
     * @return 有效的 access_token
     */
    String getAccessToken() {
        TokenCache cached = tokenCache.get();
        if (cached != null && cached.isValid()) {
            log.debug("使用缓存的 access_token，过期时间: {}", cached.expiresAt);
            return cached.token;
        }

        synchronized (tokenRefreshLock) {
            cached = tokenCache.get();
            if (cached != null && cached.isValid()) {
                log.debug("使用并发刷新后的 access_token，过期时间: {}", cached.expiresAt);
                return cached.token;
            }

            log.info("获取新的 access_token");
            try {
                String response = webClient.get()
                        .uri(apiBaseUrl + TOKEN_PATH + "?grant_type=client_credential&appid={appid}&secret={secret}",
                                appId, appSecret)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(10));

                JsonNode root = objectMapper.readTree(response);
                checkError(root);

                String token = requiredText(root, "access_token");
                int expiresIn = root.path("expires_in").asInt(7200);

                // 提前 5 分钟刷新，避免边界过期
                long cacheSeconds = Math.max(1, expiresIn - 300L);
                TokenCache newCache = new TokenCache(token, Instant.now().plusSeconds(cacheSeconds));
                tokenCache.set(newCache);

                log.info("access_token 获取成功，有效期: {} 秒", expiresIn);
                return token;

            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException("获取微信 access_token 失败: " + exception.getMessage(), exception);
            }
        }
    }

    /**
     * 创建草稿。
     *
     * <p>WeChat API: POST /cgi-bin/draft/add?access_token=ACCESS_TOKEN</p>
     *
     * @param accessToken 有效的 access_token
     * @param article     待发布的文章
     * @return 草稿的 media_id
     */
    String createDraft(String accessToken, Article article) {
        return createDraft(accessToken, article, WeChatContentRenderer.render(article), thumbMediaId);
    }

    String createDraft(String accessToken, Article article, String content, String resolvedThumbMediaId) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode articleNode = body.putArray("articles").addObject();
        articleNode.put("title", article.getTitle());
        articleNode.put("author", defaultAuthor);
        articleNode.put("content", content);
        articleNode.put("need_open_comment", 0);
        articleNode.put("only_fans_can_comment", 0);

        // 可选的来源 URL（声明原创时用于追溯）
        if (article.getSource() != null && !article.getSource().isBlank()) {
            articleNode.put("content_source_url", article.getSource());
        }

        // 可选的封面图片 media_id（需要预先通过素材管理上传）
        if (resolvedThumbMediaId != null && !resolvedThumbMediaId.isBlank()) {
            articleNode.put("thumb_media_id", resolvedThumbMediaId);
        }

        log.debug("创建草稿请求: title={}", article.getTitle());

        try {
            String response = webClient.post()
                    .uri(apiBaseUrl + DRAFT_ADD_PATH + "?access_token={token}", accessToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(writeJson(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            JsonNode root = objectMapper.readTree(response);
            checkError(root);

            return requiredText(root, "media_id");

        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("创建微信草稿失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查微信 API 响应中的错误码，非零则抛出异常。
     */
    private void checkError(JsonNode root) {
        int errcode = root.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = root.path("errmsg").asText("未知错误");
            throw new RuntimeException("微信 API 错误 [" + errcode + "]: " + errmsg);
        }
    }

    private static String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("微信 API 响应缺少字段: " + field);
        }
        return value;
    }

    private String writeJson(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化微信请求", exception);
        }
    }

    /**
     * access_token 缓存条目。
     */
    static class TokenCache {
        final String token;
        final Instant expiresAt;

        TokenCache(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
