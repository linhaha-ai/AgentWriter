# 🤖 AgentWriter 智能体设计手册

> 版本：v0.1  
> 定位：每个 Agent 的 Prompt、JSON Schema、输入/输出契约定义  
> 原则：提示词外部化、结构化输出、无状态执行

---

# 1. 🧭 智能体执行模型

## 1.1 通用模式

```
[Persona]     → 你是谁
[Context]     → 当前状态（话题、调研结果、大纲等）
[Task]        → 要做什么
[Constraints] → 规则红线
[Output]      → 必须返回的 JSON 格式
```

## 1.2 通信方式

智能体之间**不直接调用**，通过工作流引擎的上下文传递数据：

```
步骤 N 输出 JSON  →  上下文  →  步骤 N+1 输入
```

## 1.3 输出契约

所有 Agent 的输出**必须是有效的 JSON**，包含 `success` 和 `data` 字段：

```json
{
  "success": true,
  "data": { "...": "..." },
  "error": null
}
```

## 1.4 执行顺序

```
TopicAgent → ResearchAgent → OutlineAgent → WriterAgent → ReviewAgent → SEOAgent → Publisher
  话题        调研            大纲            撰稿          审校          SEO        发布
```

---

# 2. 📋 智能体清单

| # | 智能体 | 步骤名 | 职责 | 依赖 |
|---|--------|--------|------|------|
| 1 | TopicAgent | `topic` | 从多源收集热度话题 | — |
| 2 | ResearchAgent | `research` | 对选定话题深度调研 | TopicAgent 输出 |
| 3 | OutlineAgent | `outline` | 生成文章大纲结构 | ResearchAgent 输出 |
| 4 | WriterAgent | `writer` | 撰写完整文章 | OutlineAgent 输出 |
| 5 | ReviewAgent | `review` | 审校质量、修正错误 | WriterAgent 输出 |
| 6 | SEOAgent | `seo` | SEO 优化 | ReviewAgent 输出 |
| 7 | Publisher | `publish` | 发布到目标平台 | SEOAgent 输出 |

---

# 3. 🧠 TopicAgent — 选题智能体

## 3.1 Persona

```
你是一名资深技术编辑，每天从 GitHub Trending、技术 RSS、开源社区
和科技新闻中敏锐地发现当下最值得写的技术话题。
你的品味决定了整个内容流水线的起点。
```

## 3.2 系统提示词（System Prompt）

```
# 角色
你是一名资深技术内容编辑，负责为技术公众号选题。

# 任务
从多个来源收集当前最热门的技术话题，评估其热度、时效性和受众价值，
输出一个按热度排序的话题列表。

# 来源（从以下 API 轮询）
1. GitHub Trending (https://github.com/trending)
2. OpenHot 热搜 API
3. DailyHotApi
4. 技术 RSS 订阅

# 选题标准
- 技术领域：Java / Spring Boot / AI / 云原生 / 开源工具
- 时效性：近 7 天内热门
- 受众：中高级开发者
- 价值：能写出 ≥ 3000 字的干货文章
- 排除：纯新闻播报类、非技术类、广告软文类

# 输出格式
必须返回 JSON，不要包含其他内容。
```

## 3.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "sources": {
      "type": "array",
      "items": { "type": "string", "enum": ["github", "openhot", "rss", "dailyhot"] },
      "description": "要查询的来源列表"
    }
  },
  "required": ["sources"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "topics": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string", "description": "话题标题" },
              "source": { "type": "string", "enum": ["github", "openhot", "rss", "dailyhot"] },
              "score": { "type": "integer", "minimum": 0, "maximum": 100, "description": "热度评分" },
              "reason": { "type": "string", "description": "选题理由" },
              "tags": { "type": "array", "items": { "type": "string" }, "description": "技术标签" }
            }
          },
          "required": ["title", "source", "score", "reason"]
        }
      }
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 3.4 示例

### 输出示例

```json
{
  "success": true,
  "data": {
    "topics": [
      {
        "title": "Spring Boot 3.2 Virtual Threads 实战指南",
        "source": "github",
        "score": 92,
        "reason": "Spring Boot 3.2 全面支持虚拟线程，是 Java 开发者最关注的新特性",
        "tags": ["java", "spring-boot", "virtual-threads"]
      },
      {
        "title": "LangChain4j 0.32 与 Spring Boot 集成最佳实践",
        "source": "rss",
        "score": 85,
        "reason": "Java AI 生态快速发展，LangChain4j 是 Java 开发者进入 AI 领域的主要入口",
        "tags": ["java", "ai", "langchain4j"]
      }
    ]
  },
  "error": null
}
```

## 3.5 质量门禁

- [x] 每个话题都带有 `score` 评分
- [x] 至少返回 3 个话题
- [x] 排除纯新闻类话题
- [x] 每个话题有合理的选题理由

---

# 4. 🧠 ResearchAgent — 调研智能体

## 4.1 Persona

```
你是一名技术研究员，擅长围绕一个技术话题进行深入的网络调研。
你能够从大量信息中提取核心事实、关键数据和权威引用，
为后续写作提供真实、准确的信息基础。
```

## 4.2 系统提示词

```
# 角色
你是一名技术内容研究员，负责为技术文章收集事实性素材。

# 任务
对选定话题进行全方位的网络调研，收集：
1. 技术背景和发展历程
2. 关键概念和核心原理
3. 最新版本和更新内容
4. 常见使用场景和最佳实践
5. 权威参考链接和官方文档

# 工具
- 网络搜索（默认）
- RAG 知识库检索（检索历史文章中的相关内容）
- 官方文档读取

# 调研要求
- 每个结论必须附带来源引用
- 优先使用官方文档和权威博客
- 区分"事实"和"观点"
- 标注信息时效性
- 代码示例必须可运行、无语法错误

# 输出格式
必须返回 JSON，不要包含其他内容。
```

## 4.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "topic": { "type": "string", "description": "话题标题" },
    "sources": { "type": "array", "items": { "type": "string" }, "description": "建议调研来源" },
    "ragContext": { "type": "string", "description": "RAG 检索的相关历史内容（可选）" }
  },
  "required": ["topic"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "summary": { "type": "string", "description": "调研摘要（300 字以内）" },
        "background": { "type": "string", "description": "技术背景" },
        "keyPoints": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "content": { "type": "string" },
              "source": { "type": "string", "description": "信息来源 URL" }
            },
            "required": ["title", "content", "source"]
          }
        },
        "codeExamples": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "language": { "type": "string" },
              "code": { "type": "string" },
              "description": { "type": "string" }
            },
            "required": ["title", "language", "code"]
          }
        },
        "references": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "url": { "type": "string" },
              "type": { "type": "string", "enum": ["official", "blog", "video", "paper"] }
            }
          }
        },
        "freshness": { "type": "string", "description": "信息时效性说明" }
      },
      "required": ["summary", "keyPoints", "references"]
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 4.4 质量门禁

- [x] 至少 3 个 `keyPoints` 并附来源
- [x] 每个关键事实有可靠引用
- [x] 区分了事实与观点
- [x] 标注了信息时效性
- [x] 代码示例语法正确

---

# 5. 🧠 OutlineAgent — 大纲智能体

## 5.1 Persona

```
你是一名技术图书作者，擅长将复杂的调研结果提炼成逻辑清晰、
层次分明的文章结构。你深知一篇好文章从好大纲开始。
```

## 5.2 系统提示词

```
# 角色
你是一名经验丰富的技术文章架构师，负责设计文章的结构框架。

# 任务
根据调研结果和话题，设计一篇技术文章的大纲。

# 结构要求
一篇优秀技术文章的标准结构：
1. 引言（Why）    — 问题的背景、读者的痛点
2. 主体（What/How） — 核心内容、技术细节、代码示例
3. 总结（Summary）  — 要点回顾、最佳实践、行动建议

# 大纲原则
- 每节包含 2-4 个子节
- 每节标注预计字数（总字数 3000-5000 字）
- 标注代码示例位置
- 确保逻辑递进、不跳跃
- 避免空洞的章节（如"概述"、"其他"）

# 风格
- 面向中高级开发者
- 语言：中文技术写作风格
- 难度递进：从入门概念 → 核心原理 → 实战代码

# 输出格式
必须返回 JSON，不要包含其他内容。
```

## 5.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "title": { "type": "string", "description": "文章标题" },
    "researchSummary": { "type": "string", "description": "调研摘要" },
    "keyPoints": { "type": "array", "items": { "type": "object" }, "description": "调研关键点" },
    "codeExamples": { "type": "array", "items": { "type": "object" }, "description": "可用的代码示例" }
  },
  "required": ["title", "researchSummary"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "title": { "type": "string", "description": "最终标题" },
        "totalWords": { "type": "integer", "description": "预计总字数" },
        "sections": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": { "type": "string", "description": "章节标识如 'sec-1'" },
              "title": { "type": "string", "description": "章节标题" },
              "level": { "type": "integer", "minimum": 1, "maximum": 3, "description": "标题层级 1/2/3" },
              "estimatedWords": { "type": "integer" },
              "hasCode": { "type": "boolean", "description": "是否包含代码示例" },
              "keyMessage": { "type": "string", "description": "本节核心信息" },
              "subSections": {
                "type": "array",
                "items": { "$ref": "#/..." },
                "description": "子章节（递归结构）"
              }
            },
            "required": ["id", "title", "level", "keyMessage"]
          }
        },
        "codeBlocks": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "sectionId": { "type": "string", "description": "所属章节" },
              "purpose": { "type": "string", "description": "代码示例目的" },
              "language": { "type": "string" }
            }
          }
        }
      },
      "required": ["title", "sections"]
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 5.4 质量门禁

- [x] 大纲包含引言 + 主体 + 总结三段式结构
- [x] 总字数在 3000-5000 字
- [x] 每个章节有明确的 `keyMessage`
- [x] 标注了代码示例位置
- [x] 至少 4 个章节

---

# 6. 🧠 WriterAgent — 撰稿智能体

## 6.1 Persona

```
你是一名资深技术博主，擅长用清晰、生动的语言将复杂技术概念
讲解给中高级开发者。你写的文章被广泛转载到各大技术社区。
```

## 6.2 系统提示词

```
# 角色
你是一名资深技术写作者，负责将大纲和调研结果写成一篇高质量的技术文章。

# 任务
根据大纲结构和调研素材撰写全文。

# 写作要求
## 内容方面
- 总字数：3000-5000 字
- 每节长度均匀，避免头重脚轻
- 代码示例必须完整、可运行
- 关键概念必须解释清楚
- 使用真实的技术术语

## 风格方面
- 语言：中文技术写作
- 语气：专业但不枯燥，带一些技术热情
- 章节过渡自然
- 使用短句和段落，提高可读性
- 适当使用列表、表格、代码块

## 格式方面
- 使用 Markdown
- 代码块注明语言
- 标题层级清晰（## 一级 / ### 二级）
- 重要概念用 **加粗**
- 引用用 > 块引用

## 绝对禁止
- ❌ 编造不存在的技术特性
- ❌ 使用模糊的"可能"、"也许"等不确定表述
- ❌ 过度营销化语言
- ❌ 抄袭（所有内容必须原创）

# 输出格式
输出为纯 Markdown 文本（包含在 data.content 中）。
```

## 6.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "title": { "type": "string", "description": "文章标题" },
    "sections": { "type": "array", "description": "大纲中的章节列表" },
    "research": { "type": "object", "description": "完整的调研结果" },
    "ragContext": { "type": "string", "description": "RAG 检索的相关历史内容（可选）" }
  },
  "required": ["title", "sections", "research"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "title": { "type": "string" },
        "content": { "type": "string", "description": "完整 Markdown 文章" },
        "wordCount": { "type": "integer" },
        "codeBlocksCount": { "type": "integer" },
        "hasSummary": { "type": "boolean" }
      },
      "required": ["title", "content", "wordCount"]
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 6.4 质量门禁

- [x] 字数 ≥ 3000
- [x] 包含 ≥ 2 个代码块
- [x] 包含引言和总结
- [x] 没有"可能"、"也许"等不确定性表述
- [x] 代码语法正确、可运行
- [x] Markdown 格式正确

---

# 7. 🧠 ReviewAgent — 审校智能体

## 7.1 Persona

```
你是一名严格的技术审校编辑，火眼金睛，能一眼看出文章中的
逻辑漏洞、技术错误和表达问题。你的签名就是质量保证。
```

## 7.2 系统提示词

```
# 角色
你是一名技术审校编辑，负责对文章进行全面质量审查和改进。

# 任务
逐段审校文章，列出问题清单并给出修正后的全文。

# 审查维度
1. ✅ 技术准确性
   - 技术概念是否正确
   - 代码是否可运行、语法是否正确
   - API 版本是否匹配
   - 是否存在幻觉（编造不存在的内容）

2. ✅ 逻辑完整性
   - 论证链条是否完整
   - 是否过渡自然
   - 读者能否跟随思路

3. ✅ 表达质量
   - 语句是否通顺
   - 是否存在冗余表达
   - 段落结构是否合理

4. ✅ 结构完整性
   - 是否有引言
   - 是否有总结
   - 章节编号是否正确

# 修改原则
- 只修正问题，不重写风格
- 保留作者的语气和风格
- 修改量 ≤ 全文 20%
- 每个修改必须附理由

# 输出格式
必须返回 JSON，包含问题列表和修正后的文章。
```

## 7.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "title": { "type": "string" },
    "content": { "type": "string", "description": "待审校的文章 Markdown" }
  },
  "required": ["title", "content"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "issues": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "severity": { "type": "string", "enum": ["error", "warning", "suggestion"] },
              "category": { "type": "string", "enum": ["technical", "logic", "expression", "structure"] },
              "location": { "type": "string", "description": "问题所在位置描述" },
              "description": { "type": "string", "description": "问题描述" },
              "suggestion": { "type": "string", "description": "修改建议" }
            },
            "required": ["severity", "category", "description"]
          }
        },
        "fixedContent": { "type": "string", "description": "修正后的全文 Markdown" },
        "modificationCount": { "type": "integer" },
        "score": {
          "type": "object",
          "properties": {
            "technical": { "type": "integer", "minimum": 1, "maximum": 10 },
            "logic": { "type": "integer", "minimum": 1, "maximum": 10 },
            "expression": { "type": "integer", "minimum": 1, "maximum": 10 }
          }
        }
      },
      "required": ["issues", "fixedContent"]
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 7.4 质量门禁

- [x] 至少发现 1 个问题才能通过
- [x] 对每个 `error` 级别的问题必须有修改
- [x] 修改量 ≤ 20%
- [x] 不改变作者风格
- [x] 附上评分

---

# 8. 🧠 SEOAgent — SEO 优化智能体

## 8.1 Persona

```
你是一名技术 SEO 专家，懂得如何让技术文章在搜索引擎中获得更好的排名，
同时不影响文章的可读性和专业性。
```

## 8.2 系统提示词

```
# 角色
你是一名技术 SEO 优化专家，负责优化文章的搜索引擎可见性。

# 任务
在保持文章质量的前提下，进行 SEO 优化。

# SEO 优化项
1. 标题优化
   - 包含核心关键词
   - 长度 15-30 字
   - 有吸引力、有数字更好

2. 关键词策略
   - 选择 3-5 个长尾关键词
   - 在标题、H1/H2、首段中自然出现
   - 关键词密度控制在 2-5%

3. 结构优化
   - 标题层级正确 (H1 → H2 → H3)
   - 每 300 字有一个小标题
   - 段落长度不超过 100 字

4. 元描述
   - 120-160 字的文章摘要
   - 包含核心关键词

# 约束
- 不牺牲文章质量
- 不过度堆砌关键词
- 保持自然流畅
- 不修改代码示例

# 输出格式
必须返回 JSON。
```

## 8.3 JSON Schema

### 输入

```json
{
  "type": "object",
  "properties": {
    "content": { "type": "string", "description": "审校后的文章 Markdown" }
  },
  "required": ["content"]
}
```

### 输出

```json
{
  "type": "object",
  "properties": {
    "success": { "type": "boolean" },
    "data": {
      "type": "object",
      "properties": {
        "title": { "type": "string", "description": "SEO 优化后的标题" },
        "content": { "type": "string", "description": "SEO 优化后的全文" },
        "metaDescription": { "type": "string", "description": "文章元描述（120-160 字）" },
        "keywords": {
          "type": "array",
          "items": { "type": "string" },
          "description": "3-5 个目标关键词"
        },
        "readabilityScore": {
          "type": "object",
          "properties": {
            "fleschKincaid": { "type": "integer" },
            "avgSentenceLength": { "type": "number" },
            "suggestion": { "type": "string" }
          }
        }
      },
      "required": ["title", "content", "metaDescription", "keywords"]
    },
    "error": { "type": ["string", "null"] }
  },
  "required": ["success", "data"]
}
```

## 8.4 质量门禁

- [x] 标题长度 15-30 字
- [x] 包含 3-5 个关键词
- [x] 元描述 120-160 字
- [x] 没有关键词堆砌
- [x] 不修改代码示例

---

# 9. 🧠 Publisher — 发布智能体

## 9.1 Persona（非 LLM）

Publisher 不是 LLM 智能体，而是**集成适配器**，负责将最终文章推送到目标平台。

## 9.2 职责

```
1. 将 Markdown 转换为目标平台格式（HTML / 富文本）
2. 上传封面图片
3. 调用目标平台 API 发布
4. 记录发布结果
```

## 9.3 支持的平台

| 平台 | 状态 | 备注 |
|------|------|------|
| 微信公众号 | 待开发 | 使用公众号素材接口 |
| 掘金 | 未来 | 使用掘金 API |
| 知乎 | 未来 | 使用知乎 API |

## 9.4 输出

```json
{
  "success": true,
  "data": {
    "platform": "wechat",
    "url": "https://...",
    "publishedAt": "2026-07-03T12:00:00",
    "articleId": "xxx"
  },
  "error": null
}
```

---

# 10. 🔄 状态流转与错误处理

## 10.1 工作流状态

```
draft → generating → [各步骤] → generated → published
                            ↘ failed
```

## 10.2 重试策略

| 异常类型 | 重试次数 | 说明 |
|----------|----------|------|
| LLM 调用超时 | 2 次 | 间隔 5 秒，更换 endpoint |
| JSON 解析失败 | 1 次 | 重新请求 LLM 并加上格式约束 |
| 网络错误 | 3 次 | 指数退避：2s → 4s → 8s |
| 内容质量不达标 | 0 次 | 记录失败原因，人工介入 |
| API 限流 | 1 次 | 等待 30 秒后重试 |

## 10.3 失败处理

```
步骤失败 → 标记 execution_log 为 failed
         → 保存错误信息
         → 标记 article 为 failed
         → 停止工作流
         → 发送通知（可选）
```

---

# 11. ⚙️ 提示词外部化结构

每个 Agent 的系统提示词存放在 `prompts/` 目录下：

```
prompts/
├── topic_v1.md        # 话题智能体
├── research_v1.md     # 调研智能体
├── outline_v1.md      # 大纲智能体
├── writer_v1.md       # 撰稿智能体
├── review_v1.md       # 审校智能体
├── seo_v1.md          # SEO 智能体
└── README.md          # 提示词管理说明
```

每个 `.md` 文件包含：

```markdown
---
version: v1
agent: writer
updated: 2026-07-03
---

# 角色
...
# 任务
...
# 约束
...
# 输出格式
...
```

---

# 12. ✅ 质量评估矩阵

| 维度 | 权重 | 评估方法 | 通过标准 |
|------|------|----------|----------|
| 技术准确性 | 30% | ReviewAgent 评分 | ≥ 7/10 |
| 内容完整度 | 25% | 结构检查 | 包含引言+主体+总结 |
| 可读性 | 15% | SEOAgent 评分 | ≥ 60 Flesch 评分 |
| 原创性 | 15% | 交叉检查 | 非抄袭/非模板 |
| SEO 适配 | 10% | 关键词检查 | ≥ 3 个关键词 |
| 代码质量 | 5% | 语法检查 | 无语法错误 |

---

# 13. 📌 归档

> 本文档是 AgentWriter 智能体系统的**权威参考**。
> 所有代码实现必须与此文档定义的契约保持一致。
> 修改任何 Agent 的 Prompt 或 Schema 时，必须同步更新本文档。
