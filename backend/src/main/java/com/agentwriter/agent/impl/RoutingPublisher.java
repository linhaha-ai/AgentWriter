package com.agentwriter.agent.impl;

import com.agentwriter.agent.Publisher;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.dto.WechatRuntimeConfig;
import com.agentwriter.entity.Article;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RoutingPublisher implements Publisher {

    private static final String WECHAT_APP_SECRET = "WECHAT_APP_SECRET";
    private static final String WECHAT_API_BASE_URL = "agentwriter.publisher.wechat.api-base-url";

    private final SettingsService settingsService;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final WeChatImageMaterialService imageMaterialService;

    @Autowired
    public RoutingPublisher(
            SettingsService settingsService,
            Environment environment,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            ObjectProvider<WeChatImageMaterialService> imageMaterialServiceProvider
    ) {
        this(
                settingsService,
                environment,
                objectMapper,
                webClientBuilder,
                imageMaterialServiceProvider.getIfAvailable()
        );
    }

    public RoutingPublisher(
            SettingsService settingsService,
            Environment environment,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            WeChatImageMaterialService imageMaterialService
    ) {
        this.settingsService = settingsService;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
        this.imageMaterialService = imageMaterialService;
    }

    public RoutingPublisher(
            SettingsService settingsService,
            Environment environment,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder
    ) {
        this(settingsService, environment, objectMapper, webClientBuilder, (WeChatImageMaterialService) null);
    }

    @Override
    public PublishResult publish(Article article) {
        WechatRuntimeConfig config = settingsService.getPublisherRuntimeConfig().wechat();
        if (!config.enabled()) {
            throw new PublisherUnavailableException("微信公众号发布器已禁用");
        }
        // 凭据优先级：环境变量 WECHAT_APP_SECRET > 界面保存的 AppSecret > yml 配置
        String appSecret = environment.getProperty(WECHAT_APP_SECRET);
        if (appSecret == null || appSecret.isBlank()) {
            appSecret = config.appSecret();
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new PublisherUnavailableException("微信公众号发布器缺少凭据配置，请在系统设置中填写或设置 WECHAT_APP_SECRET");
        }
        String apiBaseUrl = environment.getProperty(WECHAT_API_BASE_URL);
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.weixin.qq.com";
        }
        return new WeChatPublisher(
                config.appId(), appSecret, config.author(), config.thumbMediaId(),
                apiBaseUrl,
                webClientBuilder.build(), objectMapper, imageMaterialService
        ).publish(article);
    }
}
