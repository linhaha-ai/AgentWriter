# 🧠 RAG 知识库策略

> 版本：v0.1  
> 定位：知识块切分、向量化存储、检索排序策略  
> 关联：AGENTS.md §4（ResearchAgent RAG 上下文）、doc/ARCHITECTURE.md

---

# 1. 整体架构

```
                  +------------------+
  文章 (Article)  |   ChunkStrategy  |  知识块 (KnowledgeChunk)
  +----------+    |  切分策略         |   +-------------------+
  | Markdown |--> |  1. 按标题分割   |-->| id               |
  | 全文      |    |  2. 语义段落合并  |   | article_id       |
  +----------+    |  3. 上下文窗口    |   | content          |
                  +------------------+   | embedding (vec)  |
                         |               | metadata (JSON)  |
                         v               | created_at       |
                  +------------------+   +-------------------+
                  |  Embedding       |          |
                  |  向量化           |          v
                  |  text → vector   |   +-------------------+
                  +------------------+   |  Vector Store     |
                         |               |  pgvector / MySQL  |
                         v               +-------------------+
                  +------------------+
                  |  Retrieval       |
                  |  查询 + 重排序    |
                  |  (RAGService)    |
                  +------------------+
```

---

# 2. 知识块切分策略 (ChunkStrategy)

## 2.1 切分流程

```
Markdown 全文
    │
    ▼
┌─────────────────────────────┐
│ Step 1: 按 ## 标题分块       │
│ 每个 ## 标题及其内容为一个块  │
│ 排除：引言、总结等短章节      │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│ Step 2: 过长块继续切分       │
│ 阈值：800 tokens / 块       │
│ 策略：按自然段落分割          │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│ Step 3: 过短块向前合并       │
│ 阈值：< 100 tokens           │
│ 合并到前一个块中             │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│ Step 4: 添加上下文窗口       │
│ 每个块附加前后块的标题/摘要   │
└─────────────┬───────────────┘
              │
              ▼
         知识块列表
```

## 2.2 切分参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `targetTokens` | 600 | 目标块大小（tokens） |
| `maxTokens` | 1000 | 最大块大小，超过则强制切分 |
| `minTokens` | 100 | 最小块大小，低于则合并 |
| `overlapTokens` | 50 | 相邻块重叠 token 数 |
| `windowSize` | 1 | 上下文窗口：附加前后 N 个块的标题 |

## 2.3 Token 估算规则

```java
// 中文文本 token 估算：1 个汉字 ≈ 2 tokens
// 英文文本 token 估算：1 个单词 ≈ 1.3 tokens
// 代码块：每行 ≈ 3 tokens
public int estimateTokens(String text) {
    int chineseChars = countChinese(text);
    int englishWords = countEnglishWords(text);
    int codeLines = countCodeLines(text);
    return chineseChars * 2 + englishWords * 1.3 + codeLines * 3;
}
```

## 2.4 元数据提取

每个知识块附带以下元数据：

```json
{
  "title": "所在章节标题",
  "level": 1,
  "tags": ["java", "spring-boot"],
  "codeLanguage": "java",
  "hasCode": true,
  "articleTitle": "原文标题",
  "publishedAt": "2026-07-03"
}
```

---

# 3. 向量存储 Schema

## 3.1 PostgreSQL (生产) — pgvector

```sql
-- 需要 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_chunks (
    id              BIGSERIAL PRIMARY KEY,
    article_id      BIGINT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    chunk_index     INT NOT NULL COMMENT '块在文章中的序号',
    title           VARCHAR(255) COMMENT '章节标题',
    content         TEXT NOT NULL COMMENT '块内容',
    tokens_count    INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
    embedding       VECTOR(1536) COMMENT 'OpenAI text-embedding-3-small 向量',
    metadata        JSONB DEFAULT '{}',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_knowledge_chunks_article_id ON knowledge_chunks(article_id);
CREATE INDEX idx_knowledge_chunks_embedding ON knowledge_chunks 
    USING IVFFLAT (embedding vector_cosine_ops) WITH (lists = 100);
```

## 3.2 MySQL (开发)

MySQL 8 原生不支持向量索引，开发阶段使用替代方案：

```sql
CREATE TABLE knowledge_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id      BIGINT NOT NULL,
    chunk_index     INT NOT NULL COMMENT '块在文章中的序号',
    title           VARCHAR(255) COMMENT '章节标题',
    content         TEXT NOT NULL COMMENT '块内容',
    tokens_count    INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
    embedding_json  JSON COMMENT '向量以 JSON 数组存储 [0.1, 0.2, ...]',
    metadata_json   JSON DEFAULT '{}',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识块（RAG 存储单元）';

CREATE INDEX idx_chunks_article_id ON knowledge_chunks(article_id);
```

## 3.3 向量维度

| Embedding 模型 | 维度 | 适用场景 |
|----------------|------|----------|
| text-embedding-3-small | 1536 | 默认（OpenAI） |
| text-embedding-3-large | 3072 | 高精度场景 |
| BGE-M3 (本地) | 1024 | 离线部署 |

---

# 4. 检索策略

## 4.1 检索流程

```
用户查询
    │
    ▼
┌─────────────────────┐
│ Query Rewrite       │
│ 改写查询（历史相关） │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ Embedding           │
│ query → vector      │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ Vector Search       │
│ cosine_sim > 0.75   │
│ topK = 5            │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ Re-rank             │
│ 1. BM25 文本匹配    │
│ 2. 时间衰减因子     │
│ 3. 最终分数 = 0.7vec│
│           + 0.3bm25 │
└─────────┬───────────┘
          │
          ▼
      Top-K 结果
```

## 4.2 检索参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `topK` | 5 | 返回的最多块数 |
| `minScore` | 0.75 | 余弦相似度阈值 |
| `timeDecayDays` | 90 | 超过此天数的文章降权 |
| `vecWeight` | 0.7 | 向量检索权重 |
| `bm25Weight` | 0.3 | 关键词检索权重 |

## 4.3 混合检索 SQL (pgvector)

```sql
WITH vector_results AS (
    SELECT id, content, title, 
           1 - (embedding <=> :query_vector) AS vec_score
    FROM knowledge_chunks
    WHERE 1 - (embedding <=> :query_vector) > :min_score
    ORDER BY vec_score DESC
    LIMIT :top_k * 2
),
bm25_results AS (
    SELECT id, content, title,
           ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', :query)) AS bm25_score
    FROM knowledge_chunks
    WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', :query)
    LIMIT :top_k * 2
)
SELECT COALESCE(v.id, b.id) AS id,
       COALESCE(v.content, b.content) AS content,
       COALESCE(v.title, b.title) AS title,
       COALESCE(v.vec_score, 0) * 0.7 + COALESCE(b.bm25_score, 0) * 0.3 AS final_score
FROM vector_results v
FULL OUTER JOIN bm25_results b ON v.id = b.id
ORDER BY final_score DESC
LIMIT :top_k;
```

---

# 5. 去重策略

同一话题可能被多次撰写，RAG 需要避免返回重复内容：

```
同话题检测：
  新文章 → 与已存知识块的标题/摘要计算余弦相似度
  similarity > 0.85 → 标记为重复，不存入 RAG
  similarity > 0.70 → 存入但降权（权重 * 0.5）

时间去重：
  同一话题在 30 天内已写过 → 跳过该话题
```

---

# 6. 更新策略

| 触发条件 | 操作 |
|----------|------|
| 新文章发布 | 提取知识块 → Embedding → 存入向量库 |
| 文章修改 | 删除旧块 → 重新提取 |
| 文章删除 | 级联删除关联知识块 |
| 定时刷新 | 无（仅写时更新，读时检索） |
| 冷启动 | 批量导入历史文章 |

---

# 7. 配置项

```yaml
agentwriter:
  rag:
    chunk:
      target_tokens: 600
      max_tokens: 1000
      min_tokens: 100
      overlap_tokens: 50
      window_size: 1
    retrieval:
      top_k: 5
      min_score: 0.75
      time_decay_days: 90
      vec_weight: 0.7
      bm25_weight: 0.3
    embedding:
      model: text-embedding-3-small
      dimensions: 1536
      api_key: ${OPENAI_API_KEY}
    dedup:
      title_similarity_threshold: 0.85
      days_threshold: 30
```

---

# 8. 与工作流的集成

```
WriterAgent 完成文章
       │
       ▼
WorkflowEngine 检测到步骤完成
       │
       ▼
┌─────────────────────┐
│ RAGService.store()  │
│ 1. ChunkStrategy    │
│ 2. Embedding        │
│ 3. 写入 knowledge_  │
│    chunks           │
└─────────────────────┘

ResearchAgent 调研时
       │
       ▼
┌─────────────────────┐
│ RAGService.retrieve │
│ (query, topK=5)     │
│ 返回相关历史块      │
└─────────────────────┘
```

---

# 9. 📌 归档

> 本文档定义了 AgentWriter 的 RAG 知识库实现策略。
> 所有存储和检索逻辑必须与此设计保持一致。
> 切换到 pgvector 后需要更新 MySQL 兼容代码。
