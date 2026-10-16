# 🗄️ AgentWriter 数据库设计

> 版本: v0.1  
> 数据库: PostgreSQL 16 + pgvector  
> 字符集: UTF-8  
> 时区: UTC-8

---

## 1. 🚀 快速开始

### 1.1 前置条件

```bash
# 安装 PostgreSQL 16
# macOS
brew install postgresql@16

# Ubuntu/Debian
sudo apt install postgresql-16 postgresql-16-pgvector

# Docker (推荐)
docker run -d \
  --name agentwriter-pg \
  -e POSTGRES_USER=agentwriter \
  -e POSTGRES_PASSWORD=agentwriter_secret \
  -e POSTGRES_DB=agentwriter \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

### 1.2 初始化数据库

```bash
# 方式一: 直接执行 SQL
psql -U agentwriter -d agentwriter -f sql/schema.sql

# 方式二: Docker 内执行
docker exec -i agentwriter-pg psql -U agentwriter -d agentwriter < sql/schema.sql
```

---

## 2. 📊 ER 关系图

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│   topic     │────<│   research   │     │ execution_log   │
│             │  1:N │              │     │                 │
└─────────────┘     └──────────────┘     └────────┬────────┘
                                                   │ 1:N
┌─────────────┐     ┌──────────────┐     ┌────────┴────────┐
│  article    │────<│  knowledge   │     │   agent_log     │
│             │  1:N │  _chunk     │     │                 │
└─────────────┘     └──────────────┘     └─────────────────┘
       │
       │ 1:N
       v
┌──────────────┐
│ execution_log│
└──────────────┘
```

---

## 3. 📋 表结构

### 3.1 article — 文章表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 文章 ID |
| title | VARCHAR(500) | NOT NULL | 文章标题 |
| content_md | TEXT | NULL | Markdown 原文 |
| content_html | TEXT | NULL | HTML 渲染内容 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'draft' | 状态: draft/generating/generated/published/failed |
| source | VARCHAR(100) | NULL | 文章来源标识 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 更新时间 |

**索引:**
- `idx_article_status` — 按状态查询
- `idx_article_created_at` — 按时间排序

---

### 3.2 topic — 话题表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 话题 ID |
| title | VARCHAR(500) | NOT NULL | 话题名称 |
| source | VARCHAR(100) | NOT NULL | 来源: github/openhot/rss/dailyhot |
| score | INT | NOT NULL DEFAULT 0 | 热度评分 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'pending' | 状态: pending/selected/done |
| reason | TEXT | NULL | 选题理由 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |

**索引:**
- `idx_topic_source` — 按来源筛选
- `idx_topic_score` — 按热度排序
- `idx_topic_status` — 按状态筛选

---

### 3.2.1 media_category / media_asset — 资源图库

`media_category` 保存用户维护的图片分类；`media_asset` 保存图库中独立存储的图片副本。素材的 `category_id` 可为空，表示未分类。自动生成图片以 `article_image_id` 关联来源文章图片，并通过唯一约束避免重复归档。

| 表 | 关键字段 | 说明 |
|------|------|------|
| media_category | name | 不区分大小写的唯一分类名称 |
| media_asset | category_id | 可空，分类删除后自动转为未分类 |
| media_asset | source | `GENERATED` 或 `UPLOADED` |
| media_asset | prompt | AI 生成素材保留的提示词，可参与搜索 |
| media_asset | storage_key | 图库独立副本的相对路径，位于图片存储根目录的 `library/` 下 |
| media_asset | article_image_id | 自动归档来源，可空且唯一 |

图库删除仅删除图库副本和元数据，不会影响原文章图片或发布流程。手动上传仅接受 PNG、JPEG、WebP，单张不超过 10MB，一次最多 10 张、总大小不超过 100MB。

---

### 3.3 research — 调研记录表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 调研记录 ID |
| topic_id | BIGINT | NOT NULL, FK → topic(id) | 关联话题 ID |
| summary | TEXT | NULL | 调研摘要 |
| raw_data | JSONB | NULL | 原始调研数据 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |

**索引:**
- `idx_research_topic_id` — 按话题查询

---

### 3.4 execution_log — 执行日志表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 日志 ID |
| step | VARCHAR(100) | NOT NULL | 步骤: topic/research/outline/writer/review/seo/publish |
| agent_name | VARCHAR(100) | NULL | 智能体名称 |
| input | JSONB | NULL | 步骤输入 |
| output | JSONB | NULL | 步骤输出 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'pending' | 状态: pending/running/success/failed |
| error_msg | TEXT | NULL | 错误信息 |
| article_id | BIGINT | NULL, FK → article(id) | 关联文章 ID |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |

**索引:**
- `idx_el_step` — 按步骤筛选
- `idx_el_status` — 按状态筛选
- `idx_el_article_id` — 按文章关联查询
- `idx_el_created_at` — 按时间排序

---

### 3.5 knowledge_chunk — 知识块表（RAG + pgvector）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 知识块 ID |
| article_id | BIGINT | NOT NULL, FK → article(id) | 来源文章 ID |
| chunk_index | INT | NOT NULL DEFAULT 0 | 块在文章中的序号 |
| title | VARCHAR(255) | NULL | 所在章节标题 |
| content | TEXT | NOT NULL | 文本块内容 |
| tokens_count | INT | NOT NULL DEFAULT 0 | 估算 token 数 |
| embedding | VECTOR(1536) | NULL | 向量嵌入 (OpenAI ada-002: 1536维) |
| metadata_json | JSONB | NULL | 元数据: {tags, codeLanguage, hasCode, articleTitle} |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 更新时间 |

**索引:**
- `idx_kc_article_id` — 按文章查询
- `idx_kc_chunk_index` — 按块序号
- `idx_kc_embedding_hnsw` — HNSW 向量近似搜索索引 (IVFFlat 替代方案)

---

### 3.6 agent_log — 智能体调用日志表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGSERIAL | PK | 记录 ID |
| log_id | BIGINT | NOT NULL, FK → execution_log(id) | 关联 execution_log ID |
| agent_name | VARCHAR(100) | NOT NULL | 智能体名称: outline/writer/review/seo |
| prompt | TEXT | NULL | 使用的提示词 |
| response | TEXT | NULL | LLM 返回结果 |
| tokens_in | INT | DEFAULT 0 | 输入 token 数 |
| tokens_out | INT | DEFAULT 0 | 输出 token 数 |
| latency_ms | INT | DEFAULT 0 | 调用耗时（毫秒） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'success' | 状态: success/failed |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | 创建时间 |

**索引:**
- `idx_al_log_id` — 按日志关联查询
- `idx_al_agent_name` — 按智能体筛选

---

## 4. 🔍 pgvector 向量搜索

### 4.1 向量维度选择

| 模型 | 维度 | 说明 |
|------|------|------|
| text-embedding-ada-002 | 1536 | OpenAI 默认 |
| text-embedding-3-small | 1536 | OpenAI 新版 |
| text-embedding-3-large | 3072 | OpenAI 高精度 |
| bge-large-zh-v1.5 | 1024 | 中文开源 |

> 本项目默认使用 1536 维（OpenAI ada-002），如需更换请修改 `VECTOR(1536)` 中的维度。

### 4.2 相似度搜索函数

```sql
-- 余弦相似度搜索（推荐）
CREATE OR REPLACE FUNCTION search_knowledge(
    query_embedding VECTOR(1536),
    match_count INT DEFAULT 5,
    match_threshold FLOAT DEFAULT 0.5
)
RETURNS TABLE (
    id BIGINT,
    article_id BIGINT,
    title VARCHAR(255),
    content TEXT,
    similarity FLOAT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT
        kc.id,
        kc.article_id,
        kc.title,
        kc.content,
        1 - (kc.embedding <=> query_embedding) AS similarity
    FROM knowledge_chunk kc
    WHERE kc.embedding IS NOT NULL
      AND 1 - (kc.embedding <=> query_embedding) > match_threshold
    ORDER BY kc.embedding <=> query_embedding
    LIMIT match_count;
END;
$$;
```

### 4.3 使用示例

```sql
-- 存储向量
INSERT INTO knowledge_chunk (article_id, chunk_index, content, embedding)
VALUES (1, 0, 'Spring Boot 3.2 支持虚拟线程...', '[0.1, 0.2, ...]'::vector(1536));

-- 相似度搜索
SELECT * FROM search_knowledge(
    '[0.1, 0.2, ...]'::vector(1536),  -- 查询向量
    5,                                  -- 返回数量
    0.5                                 -- 相似度阈值
);

-- 按文章筛选的搜索
SELECT kc.*, 1 - (kc.embedding <=> $1) AS similarity
FROM knowledge_chunk kc
WHERE kc.article_id = 1
  AND kc.embedding IS NOT NULL
ORDER BY kc.embedding <=> $1
LIMIT 5;
```

---

## 5. ⚙️ 自动更新时间戳

```sql
-- 自动更新 updated_at 的触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要的表创建触发器
CREATE TRIGGER trigger_article_updated_at
    BEFORE UPDATE ON article
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_knowledge_chunk_updated_at
    BEFORE UPDATE ON knowledge_chunk
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

---

## 6. 📝 完整建表 SQL

> 以下 SQL 可直接执行，包含所有表、索引、扩展和函数。

```sql
-- 见 sql/schema.sql
```

---

## 7. 🔧 连接配置

### 7.1 application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentwriter
    username: agentwriter
    password: ${DB_PASSWORD:agentwriter_secret}
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # 生产环境用 validate，开发环境用 update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 25
        order_inserts: true
        order_updates: true
```

### 7.2 Maven 依赖

```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- pgvector for Java -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

---

## 8. 📊 性能优化

### 8.1 HNSW 索引参数

```sql
-- HNSW 索引（推荐，构建慢但查询快）
CREATE INDEX idx_kc_embedding_hnsw ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- IVFFlat 索引（备选，构建快但需要定期 REINDEX）
-- 需要先有数据再创建，否则索引效率低
-- CREATE INDEX idx_kc_embedding_ivfflat ON knowledge_chunk
--     USING ivfflat (embedding vector_cosine_ops)
--     WITH (lists = 100);
```

### 8.2 查询优化建议

```sql
-- 1. 首先用向量索引缩小范围
-- 2. 再用业务条件过滤
SELECT * FROM knowledge_chunk
WHERE article_id = 123
  AND embedding IS NOT NULL
ORDER BY embedding <=> $1
LIMIT 10;

-- 避免全表向量扫描，始终带 WHERE 条件
```

---

## 9. 📌 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 数据库 | PostgreSQL 16 | 原生支持 JSONB、向量搜索、全文检索 |
| 向量扩展 | pgvector | 开源、与 PG 深度集成、支持 HNSW |
| 向量维度 | 1536 | OpenAI ada-002 默认维度 |
| 时间戳 | TIMESTAMPTZ | 时区安全，避免时区问题 |
| JSON 类型 | JSONB | 支持索引、查询性能优于 JSON |
| 向量索引 | HNSW | 查询性能优于 IVFFlat，适合读多写少 |

---

> 本文档是 AgentWriter 数据库的**权威参考**。
> 修改表结构时必须同步更新此文档和 `sql/schema.sql`。
