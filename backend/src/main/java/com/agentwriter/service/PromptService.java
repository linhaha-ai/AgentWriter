package com.agentwriter.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词加载服务 - 从 prompts/ 目录加载外部化提示词文件
 *
 * <p>根据 AGENTS.md 设计，每个 Agent 的完整系统提示词存储在独立的 .md 文件中。
 * 本服务负责在应用启动时加载这些文件并缓存，供各 Agent 在运行时获取。</p>
 *
 * <p>提示词文件命名规范：{agentName}_v{version}.md，例如 topic_v1.md</p>
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    @Value("${agentwriter.prompts.location:classpath:prompts/}")
    private String promptsLocation;

    @Value("${agentwriter.prompts.version:v1}")
    private String defaultVersion;

    /**
     * 应用启动时预加载所有提示词文件到缓存
     */
    @PostConstruct
    public void init() {
        log.info("初始化提示词加载服务: location={}, version={}", promptsLocation, defaultVersion);
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            String searchPath = promptsLocation.endsWith("/") ? promptsLocation + "*.md" : promptsLocation + "/*.md";
            Resource[] resources = resolver.getResources(searchPath);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    String filename = resource.getFilename();
                    if (filename != null && filename.endsWith(".md")) {
                        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        String key = filename.replace(".md", "");
                        promptCache.put(key, content);
                        log.debug("已加载提示词: {} ({} 字节)", key, content.length());
                    }
                }
            }
            log.info("提示词加载完成: 共 {} 个文件", promptCache.size());
        } catch (IOException e) {
            log.warn("提示词文件加载失败: {}", e.getMessage());
        }
    }

    /**
     * 加载指定 Agent 的系统提示词
     *
     * @param agentName 智能体名称，如 "topic"、"research"、"writer"
     * @return 完整的系统提示词文本
     * @throws IllegalStateException 如果提示词文件未加载
     */
    public String loadPrompt(String agentName) {
        String key = agentName + "_" + defaultVersion;
        String prompt = promptCache.get(key);
        if (prompt == null) {
            log.warn("未找到提示词文件: {} (key={})", agentName, key);
            return getDefaultPrompt(agentName);
        }
        return prompt;
    }

    /**
     * 加载指定版本的 Agent 提示词
     *
     * @param agentName 智能体名称
     * @param version   版本号，如 "v1"、"v2"
     * @return 完整的系统提示词文本
     */
    public String loadPrompt(String agentName, String version) {
        String key = agentName + "_" + version;
        return promptCache.getOrDefault(key, loadPrompt(agentName));
    }

    /**
     * 重新加载所有提示词（用于热更新场景）
     */
    public void reload() {
        promptCache.clear();
        init();
    }

    /**
     * 获取已加载的提示词数量
     */
    public int getLoadedCount() {
        return promptCache.size();
    }

    /**
     * 当提示词文件不存在时返回的默认提示词
     */
    private String getDefaultPrompt(String agentName) {
        return """
                # 角色
                你是 AgentWriter 系统中的 %s 智能体。
                
                # 任务
                根据输入数据执行你的职责，返回结构化的 JSON 结果。
                
                # 约束
                - 只返回 JSON，不包含其他内容
                - 始终包含 success 和 data 字段
                """.formatted(agentName);
    }
}
