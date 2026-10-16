# 📘 AgentWriter 项目文档

> 版本：v0.1  
> 类型：AI 可执行 PRD（产品需求文档）

---

# 1. 🧭 项目概述

## 1.1 背景

AgentWriter 是一个面向技术创作者的 AI 内容生成系统。

当前技术写作流程存在以下问题：

- 找热点耗时
- 搜索资料分散
- AI 写作缺乏结构
- 内容质量不稳定
- 发布流程繁琐
- 缺乏知识复用能力

---

## 1.2 解决方案

AgentWriter 通过 **多智能体 + 深度调研 + RAG** 构建完整内容生产链：

```
热点发现 → 深度调研 → 内容规划 → 写作 → 审核 → 排版 → 发布 → 知识沉淀
```

---

## 1.3 产品定义

AgentWriter =

> 面向技术写作的 AI 内容流水线系统

不是聊天工具，不是通用智能体，而是：

👉 **面向"技术文章生产"的垂直系统**

---

# 2. 🎯 目标

## 2.1 核心目标（MVP）

每天自动生成一篇可发布的技术公众号文章：

- Java / Spring Boot
- AI 工程实践
- 开源工具解析
- 效率工具

---

## 2.2 成功标准

MVP 成功标准：

- 自动生成 ≥ 3000 字文章
- 内容结构完整（标题 / 小节 / 代码 / 总结）
- 可直接发布到公众号
- 质量达到"可读 + 有逻辑 + 非胡编"

---

## 2.3 时间缩短目标

```
之前：2 ~ 3 小时
之后：10 ~ 20 分钟审核
```

---

# 3. ❌ 非目标（非常重要）

第一版本**禁止扩展**：

- ❌ SaaS 多租户
- ❌ 小红书 / 知乎多平台
- ❌ 视频生成
- ❌ 插件市场
- ❌ 工作流设计器
- ❌ 手机 App
- ❌ 商业化系统
- ❌ 智能体市场

---

# 4. 👤 目标用户

## 主要用户

- 项目开发者本人（唯一用户）

## 未来用户（可选）

- 技术博主
- Java / AI 开发者
- 独立开发者

---

# 5. 🔁 核心工作流（系统契约）

这是整个系统的**不可变核心流程**

```
话题选择
    ↓
深度调研
    ↓
大纲生成
    ↓
文章撰写
    ↓
审核与优化
    ↓
Markdown 生成
    ↓
发布
    ↓
知识存储（RAG）
```

👉 所有智能体必须遵循该流程

---

# 6. 🤖 智能体设计

## 6.1 智能体列表

### 1️⃣ 话题智能体

**输入：**
- GitHub Trending
- OpenHot
- RSS
- DailyHotApi

**输出：**
```json
{
  "title": "",
  "topic": "",
  "source": "",
  "reason": ""
}
```

---

### 2️⃣ 调研智能体

**目标：** 收集事实性内容

**工具：**
- Tavily
- Firecrawl
- 网络搜索

**输出：**
```json
{
  "summary": "",
  "key_points": [],
  "references": []
}
```

---

### 3️⃣ 大纲智能体

**输入：** 调研结果

**输出：**
```json
{
  "title": "",
  "sections": []
}
```

---

### 4️⃣ 撰稿智能体

**目标：** 生成完整文章

**输出：**
- Markdown 文章（3000-5000 字）
- 必须包含：
  - 章节标题
  - 代码块
  - 总结

---

### 5️⃣ 审核智能体

职责：

- 消除幻觉
- 提升可读性
- 确保结构完整
- 修正逻辑问题

输出：
```text
改进后的 markdown
```

---

### 6️⃣ SEO 智能体

任务：

- 优化标题
- 生成关键词
- 提升可读性评分

---

### 7️⃣ 发布智能体

支持：

- 微信公众号

输出：

```json
{
  "title": "",
  "content_html": "",
  "cover_image": ""
}
```

---

# 7. 🧠 RAG（知识系统）

## 7.1 目的

存储所有已生成的文章以便复用：

- 避免重复内容
- 提升一致性
- 复用技术解释

---

## 7.2 处理流程

```
Markdown 文章
      ↓
文本分块
      ↓
Embedding 向量化
      ↓
向量数据库（pgvector）
      ↓
后续写作时检索
```

---

## 7.3 存储实体

- 文章（Article）
- 文本块（Chunk）
- 向量嵌入（Embedding）
- 标签（Tag）
- 来源（Source）

---

# 8. 🧱 系统架构

## 8.1 高层架构

```
后端（Spring Boot 4）
        ↓
智能体编排器
        ↓
LLM 层（OpenAI / DeepSeek / Qwen）
        ↓
工具层（搜索 / RAG / 网络）
        ↓
发布层
```

---

## 8.2 模块划分

```
agentwriter-backend
├── controller    # 控制器
├── service       # 服务层
├── agent         # 智能体
├── workflow      # 工作流
├── research      # 调研
├── writer        # 撰稿
├── topic         # 话题
├── publisher     # 发布
├── rag           # 知识库
├── common        # 公共模块
```

---

# 9. 🗄 数据库设计（MVP）

## 9.1 表：article（文章表）

```sql
id BIGINT
title TEXT
content_md LONGTEXT
content_html LONGTEXT
status VARCHAR
source VARCHAR
created_at TIMESTAMP
```

---

## 9.2 表：topic（话题表）

```sql
id BIGINT
title TEXT
source TEXT
score INT
status VARCHAR
created_at TIMESTAMP
```

---

## 9.3 表：research（调研记录表）

```sql
id BIGINT
topic_id BIGINT
summary TEXT
raw_data JSON
created_at TIMESTAMP
```

---

## 9.4 表：execution_log（执行日志表）

```sql
id BIGINT
agent_name VARCHAR
input JSON
output JSON
status VARCHAR
created_at TIMESTAMP
```

---

# 10. 🔧 API 设计

## 10.1 生成文章

```
POST /api/article/generate
```

请求：
```json
{
  "topic": "Spring Boot 4 新特性"
}
```

响应：
```json
{
  "article_id": 1,
  "status": "generated"
}
```

---

## 10.2 获取文章

```
GET /api/article/{id}
```

---

## 10.3 发布文章

```
POST /api/article/publish/{id}
```

---

# 11. 🧠 提示词管理

## 规则：

- 提示词不得硬编码在 Java 代码中
- 存放在 `/prompts/` 目录下
- 带版本管理

示例：

```
prompts/
 ├── writer_v1.md
 ├── reviewer_v1.md
 ├── seo_v1.md
```

---

# 12. ⚙️ 执行引擎

系统使用**流水线执行器**

```
工作流引擎：

话题 → 调研 → 大纲 → 撰稿 → 审核 → SEO → 发布
```

每个步骤：

- 输入
- 提示词
- 输出
- 重试策略

---

# 13. 📦 项目结构

```
agentwriter
├── backend          # 后端
│   ├── agent        #   智能体
│   ├── workflow     #   工作流
│   ├── topic        #   话题
│   ├── research     #   调研
│   ├── writer       #   撰稿
│   ├── publisher    #   发布
│   ├── rag          #   知识库
│   └── common       #   公共
│
├── prompts          # 提示词
├── docs             # 文档
├── scripts          # 脚本
├── docker           # Docker 配置
```

---

# 14. 🧑‍💻 编码规范

## Java 规范

- Java 21
- Spring Boot 4
- RESTful API
- DDD-lite 架构
- 控制器中不包含业务逻辑
- 统一响应格式

---

## AI 规范

- 提示词外部化
- LLM 提供商抽象
- 无状态智能体执行
- 所有输出尽量使用结构化 JSON

---

# 15. 🚫 AI 开发约束

AI 开发中**禁止**：

- 硬编码提示词
- 在控制器中混入工作流逻辑
- 跳过调研步骤
- 无大纲直接生成文章
- 绕过审核智能体

---

# 16. 🚀 MVP 开发路线图

## 阶段一

- 话题收集
- 基础调研
- 单撰稿智能体
- Markdown 输出

---

## 阶段二

- 多智能体系统
- 审核智能体
- SEO 优化

---

## 阶段三

- 微信公众号发布
- 图片生成

---

## 阶段四

- RAG 系统
- 知识复用

---

# 17. 📊 成功指标

MVP 成功 = 

- 每天 1 篇全自动文章
- ≥ 3000 字
- 可读的技术内容
- 核心事实无幻觉
- 可直接发布

---

# 18. 🧭 核心原则

> 简单 > 复杂  
> 工作流 > 提示词  
> 系统 > 单次 LLM 调用

---

# 19. 最终定义

AgentWriter 是：

> 一个确定性的多智能体内容流水线系统，用于技术写作自动化。

不是聊天机器人。

不是提示词工具。

而是一个**内容生产系统**。
