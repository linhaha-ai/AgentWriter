# ⚡ 异常处理与重试策略

> 版本：v0.1  
> 关联：AGENTS.md §10、doc/ARCHITECTURE.md  
> 实现：ErrorCategory.java、RetryPolicy.java、WorkflowEngine.java

---

# 1. 错误分类体系

## 错误类型枚举

| 分类 | 可重试 | 典型场景 |
|------|--------|----------|
| `LLM_TIMEOUT` | ✅ | LLM API 响应超时 |
| `NETWORK_ERROR` | ✅ | 连接重置、DNS 解析失败 |
| `RATE_LIMITED` | ✅ | API 429、限流 |
| `LLM_EMPTY_RESPONSE` | ✅ | LLM 返回空或无效 JSON |
| `JSON_PARSE_ERROR` | ✅ | 响应 JSON 解析失败 |
| `INVALID_INPUT` | ❌ | 提示词格式错误、必填字段缺失 |
| `AUTH_ERROR` | ❌ | API Key 过期/无效、401/403 |
| `QUALITY_FAILURE` | ❌ | 内容质量不达标 |
| `DATABASE_ERROR` | ❌ | SQL 异常、连接池耗尽 |
| `UNKNOWN` | ❌ | 其他未分类错误 |

## 分类规则

```java
ErrorCategory category = ErrorCategory.classify(exception);
// 根据异常消息中的关键词自动匹配分类
```

---

# 2. 重试策略

## 配置表（与 AGENTS.md §10.2 一致）

| 错误类型 | 最大重试次数 | 间隔 | 退避策略 |
|----------|-------------|------|----------|
| LLM_TIMEOUT | 2 | 5s | 固定间隔 |
| NETWORK_ERROR | 3 | 2s→4s→8s | 指数退避 |
| RATE_LIMITED | 1 | 30s | 固定间隔 |
| LLM_EMPTY_RESPONSE | 1 | 0s | 立即 |
| JSON_PARSE_ERROR | 1 | 0s | 立即 |
| 不可重试 | 0 | — | 不重试 |

## 代码示例

```java
// RetryPolicy 自动处理重试逻辑
retryPolicy.executeWithRetry(() -> {
    step.execute(context);
    return true;
}, step.name());
```

---

# 3. 工作流错误处理流程

```
步骤开始
    │
    ▼
创建 execution_log (status=pending)
    │
    ▼
┌──────────────────────────────────┐
│ retryPolicy.executeWithRetry()   │
│  ├─ 第一次执行                   │
│  ├─ ❌ LLM_TIMEOUT → 等待 5s     │
│  ├─ 🔄 重试 1/2                  │
│  ├─ ❌ NETWORK_ERROR → 等待 2s   │
│  ├─ 🔄 重试 2/2                  │
│  │                               │
│  └─ ✅ 成功 → 继续下一步          │
│      ❌ 耗尽 → 标记失败            │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│ success  → status=success        │
│ failed   → status=failed         │
│          → article.status=failed │
│          → 工作流终止              │
└──────────────────────────────────┘
```

---

# 4. 降级策略

## LLM 服务降级

| 条件 | 降级行为 |
|------|----------|
| LLM API 全部不可用 | 使用 DefaultLLMService 桩返回结构化占位数据，工作流不中断 |
| 单一模型失败 | 自动切换备选模型（gpt-4o-mini → gpt-3.5-turbo → deepseek-chat） |
| 连续 N 次重试失败 | 标记步骤为 failed，保留已执行步骤的结果 |

## 数据层降级

| 条件 | 降级行为 |
|------|----------|
| MySQL 不可用 | 启动本地 H2 内存数据库（开发模式） |
| RAG 检索失败 | 返回空结果，调研 Agent 只依赖网络搜索 |
| Redis 不可用 | 跳过缓存，直接查数据库 |

---

# 5. 审计日志

每个步骤的执行结果完整记录到 `execution_log` 表：

```json
{
  "step": "writer",
  "status": "failed",
  "error_msg": "[LLM_TIMEOUT] 连接 api.openai.com 超时",
  "input": "{...}",
  "output": null,
  "created_at": "2026-07-03T08:00:00"
}
```

---

# 6. 📌 归档

> 本文档定义了 AgentWriter 的异常处理和重试策略。
> ErrorCategory 和 RetryPolicy 组件实现了本文档定义的全部规则。
> 所有工作流步骤必须通过 RetryPolicy 执行，而不是自行处理异常。
