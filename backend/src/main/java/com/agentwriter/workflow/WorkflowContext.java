package com.agentwriter.workflow;

import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流上下文 - 在步骤之间传递数据
 */
public class WorkflowContext {

    private final Article article;
    private final WorkflowRequest request;
    private final Map<String, Object> structuredResults = new HashMap<>();
    private String currentOutput;

    public WorkflowContext(Article article, WorkflowRequest request) {
        this.article = article;
        this.request = request;
    }

    public Article getArticle() {
        return article;
    }

    public WorkflowRequest getRequest() {
        return request;
    }

    public String getCurrentOutput() {
        return currentOutput;
    }

    public void setCurrentOutput(String currentOutput) {
        this.currentOutput = currentOutput;
    }

    public void putResult(String key, Object result) {
        structuredResults.put(requireKey(key), Objects.requireNonNull(result, "结果不能为空"));
    }

    public <T> T getResult(String key, Class<T> resultType) {
        String checkedKey = requireKey(key);
        Objects.requireNonNull(resultType, "结果类型不能为空");

        Object result = structuredResults.get(checkedKey);
        if (result == null) {
            throw new IllegalStateException("未找到键对应的结构化结果: " + checkedKey);
        }
        if (!resultType.isInstance(result)) {
            throw new IllegalStateException("键 '" + checkedKey + "' 的结构化结果类型为 "
                    + result.getClass().getName() + "，不是 " + resultType.getName());
        }
        return resultType.cast(result);
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("结果键不能为空");
        }
        return key;
    }
}
