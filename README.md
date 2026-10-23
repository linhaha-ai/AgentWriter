# 🚀 AgentWriter

> 基于多智能体 + 深度调研 + RAG 的 AI 写作助手

AgentWriter 是一个面向技术创作者的 AI 内容生成系统。它能够自动发现热点话题、进行深度调研，并生成高质量的技术文章，可保存为微信公众号草稿，由运营者在公众平台审核发布。

---

## ✨ 什么是 AgentWriter？

AgentWriter 不仅仅是一个 AI 写作工具。

它是一个**多智能体内容流水线**，模拟真实的编辑团队工作流程：

- 🔎 发现热点话题
- 🧠 利用网络资源进行深度调研
- 🧩 规划文章结构
- ✍️ 撰写技术内容
- 🧪 审核与优化
- 📝 格式化为 Markdown
- 📢 保存到微信公众号草稿箱
- 📚 存储知识以便复用（RAG）

---

## 🎯 核心目标

> 每天自动生成一篇可发布的高质量技术文章

将内容创作时间从：

```
2-3 小时人工撰写
```

减少到：

```
10-20 分钟审核与发布
```

---

## 🧠 系统工作流程

```
GitHub Trending / OpenHot / RSS
            ↓
        话题选择
            ↓
        深度调研
            ↓
        大纲生成
            ↓
        文章撰写
            ↓
        审核优化
            ↓
        SEO 优化
            ↓
       Markdown 生成
            ↓
       保存公众号草稿（微信公众号）
            ↓
     知识库（RAG 存储）
```

---

## 🤖 多智能体架构

AgentWriter 使用多个专门的 AI 智能体：

| 智能体 | 职责 |
|------|----------------|
| 话题智能体 | 选择热门话题 |
| 调研智能体 | 收集并总结信息 |
| 大纲智能体 | 设计文章结构 |
| 撰写智能体 | 生成完整文章 |
| 审核智能体 | 提升质量与准确性 |
| SEO 智能体 | 优化标题与关键词 |
| 发布智能体 | 保存公众号草稿 |

---

## 🔥 功能特性

- 📊 热点话题聚合（GitHub / RSS / OpenHot / DailyHotApi）
- 🔍 AI 驱动的深度调研（Tavily / Firecrawl / 浏览器工具）
- 🤖 多智能体协作系统
- 📝 高质量 Markdown 生成
- 📰 微信公众号草稿箱保存支持
- 🧠 基于 RAG 的知识记忆系统
- 🔁 内容复用与去重
- 🧩 模块化智能体架构
- ⚡ 全自动化工作流

## 🔌 同步生成接口

- `POST /api/articles/generate`：接收已持久化的话题 `topicId`，使用该话题的标题同步生成并保存文章。
- `POST /api/workflow/start`：接收调用方明确提供的 `title`，同步执行工作流；不会用 `topicId` 查询或替换该标题。

## 📡 实时文章创建

创建向导调用 `POST /api/articles/step-generate` 后会收到文章 ID，并通过 SSE 获取生成状态：

- `GET /api/articles/{id}/events`：`text/event-stream` 事件流。连接成功先发送 `snapshot`，之后推送 `progress`、`log`、`completed` 或 `failed`。
- `GET /api/articles/{id}/progress`：返回与 SSE 快照一致的当前进度和安全阶段日志，适用于查询与故障排查。

日志只包含阶段名称、状态、时间和安全的失败类别，不包含文章正文、Prompt、模型原始响应或异常原文。完成事件会自动进入只读文章预览页，用户可继续编辑或返回文章列表。

若部署在 Nginx 等反向代理后，需关闭 SSE 路径的响应缓冲，并适当延长读取超时：

```nginx
location /api/articles/ {
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 1h;
}
```

---

## 🏗 架构概览

```
+----------------------+
|     内容源           |
|----------------------|
| GitHub Trending      |
| OpenHot              |
| RSS 订阅             |
| 新闻 API             |
+----------+-----------+
           |
           v
+----------------------+
|     话题选择         |
+----------+-----------+
           |
           v
+----------------------+
|    深度调研智能体    |
+----------+-----------+
           |
           v
+----------------------+
|    多智能体系统      |
|  (撰稿 / 审核)      |
+----------+-----------+
           |
           v
+----------------------+
|   Markdown 生成器    |
+----------+-----------+
           |
           v
+----------------------+
| 存草稿（微信公众号） |
+----------+-----------+
           |
           v
+----------------------+
|  知识库（RAG 存储）  |
+----------------------+
```

---

## 🧰 技术栈

### 后端
- Java 21
- Spring Boot 4
- 整洁架构（DDD-lite）

### AI 层
- OpenAI / DeepSeek / Qwen / Gemini
- LangChain4j

### 搜索与调研
- Tavily API
- Firecrawl
- Jina Reader

### 数据存储
- PostgreSQL
- Redis
- pgvector（RAG 向量存储）

### 前端（可选仪表盘）
- Vue 3
- Naive UI

### 基础设施
- Docker
- MinIO（图片存储）

---

## 📦 项目结构

```
agentwriter/
├── backend/
│   ├── agent/       # 智能体模块
│   ├── topic/       # 话题模块
│   ├── research/    # 调研模块
│   ├── writer/      # 撰写模块
│   ├── publisher/   # 发布模块
│   ├── rag/         # 知识库模块
│   └── common/      # 公共模块
│
├── frontend/        # 前端界面
│
├── docs/            # 文档
│
├── prompts/         # 提示词管理
│
└── scripts/         # 工具脚本
```

---

## 🚀 快速开始

```bash
# 克隆项目
git clone https://github.com/yourname/agentwriter.git

cd agentwriter

# 启动后端（需已安装 Maven 3.9+）
mvn spring-boot:run
```

---

## 🧪 输出示例

### 输入：
```
GitHub Trending: "Spring Boot 4 new features"
```

### 输出：
- 标题：你必须知道的 10 个 Spring Boot 4 新特性
- 字数：3000+ 字
- 格式：Markdown
- 状态：可保存为微信公众号草稿

---

## 🧭 开发路线图

### v0.1（MVP）✅
- [x] 话题收集
- [x] 基础调研
- [x] 单智能体写作
- [x] Markdown 输出

### v0.2 ✅
- [x] 多智能体系统
- [x] 审核智能体
- [x] SEO 优化

### v0.3 ✅
- [x] 微信公众号发布
- [x] 图片生成
- [x] 定时调度系统

### v1.0 ✅
- [x] RAG 知识库
- [x] 内容记忆系统
- [x] 仪表盘 UI

### 图片生成实施进度（v0.3）✅
- [x] 新增文章图片持久化、数据库迁移及本地图片资产托管。
- [x] 接入 ComfyUI API 工作流校验、异步提交、轮询和结果下载流程。
- [x] 支持 1 张封面图和前两个 H2 对应的 2 张正文插图建议、提示词编辑、预览与 Markdown 插入。
- [x] 发布公众号时上传已插入的正文图片，并优先使用生成封面作为缩略图。
- [x] 补充后端单元测试、控制器测试、发布器测试及前端构建验证。

### v1.1（上线准备）
- [ ] 导入已验证的 ComfyUI “Save API Format” 工作流，并配置 `AGENTWRITER_IMAGE_*` 环境变量。
- [ ] 在真实 ComfyUI 与微信公众号凭据环境完成生成、重开预览、草稿发布的端到端验收。
- [ ] 确认部署目录为可持久化存储，并制定生成图片的备份与清理策略。
- [ ] 根据微信公众号图片限制增加格式转换、尺寸约束和自动压缩，提前阻断不合规图片。

### 持续优化
- [ ] 为 ComfyUI 任务增加取消、有限重试、应用重启后的恢复能力及更完整的失败诊断。
- [ ] 将依赖外部网络和真实等待的测试替换为可控桩件，缩短完整回归测试耗时。
- [ ] 增加图片生成与公众号上传的运行日志、指标和告警，便于生产环境排障。

### ComfyUI 图片生成配置

图片生成默认关闭。部署时设置 `AGENTWRITER_IMAGE_ENABLED=true`、
`AGENTWRITER_IMAGE_COMFY_BASE_URL`、`AGENTWRITER_IMAGE_WORKFLOW_TEMPLATE` 和
`AGENTWRITER_IMAGE_STORAGE_DIRECTORY`。工作流必须由 ComfyUI 的 “Save API Format” 导出，
并包含以下占位符：`__AGENTWRITER_PROMPT__`、`__AGENTWRITER_NEGATIVE_PROMPT__`、
`__AGENTWRITER_WIDTH__`、`__AGENTWRITER_HEIGHT__`。

### 外部调研与 LLM 超时配置

调研搜索与 Agnes/OpenAI 兼容 LLM 调用均会复用 `AGENTWRITER_PROXY_HOST` 与
`AGENTWRITER_PROXY_PORT` 访问外网。若本地网络需要代理，请确保该代理进程正在监听对应地址和端口；
代理未启动时，LLM 会返回明确的网络连接错误。
Jina 连接超时和响应超时可分别通过 `AGENTWRITER_RESEARCH_CONNECT_TIMEOUT_SECONDS`（默认 15 秒）和
`AGENTWRITER_RESEARCH_RESPONSE_TIMEOUT_SECONDS`（默认 45 秒）调整；失败时会降级为 LLM 调研。

长文生成通过 `AGENTWRITER_LLM_TIMEOUT` 配置单次 LLM 请求超时，默认 120 秒，最小允许 60 秒。
工作流对可重试错误统一执行最多两次重试，LLM 客户端本身不再嵌套重试，避免一次生成请求被重复放大。

---

## 📚 核心理念

AgentWriter 建立在这样一个简单的想法之上：

> AI 不应该只是生成文字——它应该像一个内容团队一样工作。

你不需要反复提示 AI，
只需要定义一个能够持续产出内容的系统。

---

## 🤝 参与贡献

欢迎各类贡献：

- 新增智能体
- 新增数据源
- 提示词优化
- 发布平台集成
- RAG 改进

---

## 📄 开源协议

Apache License 2.0，详见 [LICENSE](./LICENSE)。
