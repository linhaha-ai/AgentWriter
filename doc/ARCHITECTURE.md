# 🏗️ AgentWriter 架构设计

> 版本：v0.1  
> 目的：AI 驱动内容流水线的系统架构

---

# 1. 🧭 系统概述

AgentWriter 是一个基于 **多智能体 + 工作流流水线 + RAG** 的内容生成系统。

核心思想：

> 将"写一篇技术文章"拆解为可编排的 AI 工作流。

---

## 1.1 高层架构

```
                ┌────────────────────┐
                │     调度器          │
                │ (定时 / 手动触发)  │
                └────────┬───────────┘
                         │
                         v
        ┌──────────────────────────────────┐
        │        工作流引擎                │
        │    (流水线编排器)               │
        └──────────────┬───────────────────┘
                       │
   ┌───────────────────┼────────────────────┐
   │                   │                    │
   v                   v                    v

话题层              调研层             智能体层
(OpenHot)          (Tavily/Web)      (撰稿/审核)

   │                   │                    │
   └───────────────────┼────────────────────┘
                       v
              内容生成层
                       │
                       v
              Markdown 渲染器
                       │
                       v
              发布器（微信公众号）
                       │
                       v
              知识库（RAG 存储）
```

---

# 2. 🧱 核心层

AgentWriter 由 6 个核心层组成：

---

## 2.1 调度层

### 职责
负责触发整个流程：

- 定时任务（每天生成文章）
- 手动触发
- API 触发

### 实现方式

- Spring Scheduler
- 或 XXL-JOB（未来计划）

---

## 2.2 选题层

### 职责

从多个来源获取热点：

- GitHub Trending
- OpenHot
- DailyHotApi
- RSS

### 输出

```json
{
  "topic": "Spring Boot 4 新特性",
  "source": "github",
  "score": 92
}
```

---

## 2.3 调研层

### 职责

对话题进行信息补全：

- 网络搜索
- 文章阅读
- 事实提取
- 来源收集

### 工具

- Tavily
- Firecrawl
- Jina Reader

### 输出

```json
{
  "summary": "...",
  "facts": [],
  "references": []
}
```

---

## 2.4 智能体层（AI 核心层）

### 职责

执行内容生成链：

```
大纲 → 撰稿 → 审核 → SEO
```

---

### 4 个智能体

#### 1️⃣ 大纲智能体
生成文章结构

```json
{
  "sections": []
}
```

---

#### 2️⃣ 撰稿智能体
生成完整文章（Markdown）

---

#### 3️⃣ 审核智能体
优化：

- 逻辑性
- 表达清晰度
- 准确性

---

#### 4️⃣ SEO 智能体
优化标题与关键词

---

## 2.5 内容层

### 职责

统一内容格式：

- Markdown
- HTML
- 元数据

---

### 输出示例

```json
{
  "title": "",
  "markdown": "",
  "html": "",
  "keywords": []
}
```

---

## 2.6 发布层

### 职责

发布到：

- 微信公众号（核心）
- 后续扩展：掘金 / 知乎

---

### 输出

```json
{
  "status": "published",
  "platform": "wechat",
  "url": ""
}
```

---

## 2.7 知识层（RAG）

### 职责

长期记忆系统：

- 存储文章
- Embedding 向量化
- 检索历史内容
- 防止重复写作

---

# 3. 🔁 工作流引擎（核心）

AgentWriter 的核心不是 AI，而是：

> 工作流编排器

---

## 3.1 执行流程

```
话题
  ↓
调研
  ↓
大纲
  ↓
撰稿
  ↓
审核
  ↓
SEO
  ↓
发布
  ↓
RAG 存储
```

---

## 3.2 执行模型

每一步都是：

```java
interface Step {
    StepResult execute(StepContext context);
}
```

---

## 3.3 流水线契约

```json
{
  "input": {},
  "output": {},
  "status": "SUCCESS",
  "nextStep": ""
}
```

---

# 4. 🤖 多智能体架构

## 4.1 智能体基础模型

```java
interface Agent {
    AgentResult run(AgentContext context);
}
```

---

## 4.2 智能体执行流程

```
上下文
  ↓
提示词
  ↓
LLM 调用
  ↓
解析输出
  ↓
返回结构化结果
```

---

## 4.3 智能体通信机制

智能体之间不直接调用。

它们通过以下方式通信：

> 工作流引擎上下文

---

# 5. 🧠 RAG 架构

## 5.1 流程

```
文章
  ↓
文本分块
  ↓
Embedding 向量化
  ↓
向量数据库（pgvector）
  ↓
检索
  ↓
注入撰稿上下文
```

---

## 5.2 目的

- 避免重复相同内容
- 提升内容一致性
- 提供历史知识参考

---

# 6. 🗄 数据架构

## 6.1 核心表

### article（文章表）

- id
- title
- content_md
- content_html
- status
- created_at

---

### topic（话题表）

- id
- title
- source
- score

---

### research（调研记录表）

- id
- topic_id
- summary
- references

---

### execution_log（执行日志表）

- id
- step
- input
- output
- status

---

### knowledge_chunk（知识块表）

- id
- content
- embedding
- article_id

---

# 7. ⚙️ 系统设计原则

## 7.1 确定性工作流

> AI 必须严格按流水线顺序执行

---

## 7.2 无状态智能体

智能体不得在内部存储状态。

所有状态保存在上下文中。

---

## 7.3 提示词隔离

提示词必须外部化存放：

```
/prompts/writer_v1.md
/prompts/reviewer_v1.md
```

---

## 7.4 LLM 抽象

支持多种模型：

- OpenAI
- DeepSeek
- Qwen
- Gemini

---

# 8. 🧩 模块设计

```
agentwriter-backend
├── controller      # 控制器层
├── workflow        # 工作流引擎
├── agent           # 智能体
│   ├── topic       #   话题
│   ├── research    #   调研
│   ├── writer      #   撰稿
│   ├── review      #   审核
│   ├── seo         #   SEO
├── publisher       # 发布模块
├── rag             # 知识库模块
├── llm             # LLM 抽象层
├── common          # 公共模块
```

---

# 9. 🚀 运行时流程（端到端）

```
1. 调度器触发任务
2. 话题层获取热门话题
3. 调研层收集数据
4. 工作流引擎启动流水线
5. 智能体按顺序执行
6. 生成 Markdown 文档
7. 发布器推送到微信公众号
8. 文章存储到 RAG 知识库
```

---

# 10. 🧠 关键设计决策

## 为什么是工作流 > 提示词？

因为：

- 提示词 = 不稳定
- 工作流 = 可控
- 系统 = 可扩展

AgentWriter 不是提示词工程。

它是：

> **AI 工作流工程**

---

# 11. 📌 关键约束

AI 必须始终遵守：

- 先选题，后写作
- 先调研，后撰稿
- 先大纲，后生成
- 先审核，后发布
- 禁止直接从 LLM 到发布的捷径

---

# 12. 🎯 未来扩展点

（v0.1 暂不实现，但架构已预留）

- 多平台发布
- 图片生成
- 视频脚本生成
- 人工审核介入
- 文章 A/B 测试
- 内容分析仪表盘

---

# 13. 🧭 架构总结

AgentWriter 是：

> 一个工作流驱动的多智能体系统，用于自动化技术内容生成。

不是聊天机器人。

不是提示词工具。

而是一个**结构化的 AI 内容流水线系统**。
