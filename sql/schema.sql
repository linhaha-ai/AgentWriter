-- ============================================================
-- AgentWriter 数据库初始化脚本（PostgreSQL + pgvector）
-- 版本: v0.1
-- 基于: doc/DATABASE.md + ARCHITECTURE.md §6
-- ============================================================

-- 启用 pgvector 扩展
CREATE
EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. 文章表
-- ============================================================
CREATE TABLE IF NOT EXISTS article
(
    id BIGSERIAL PRIMARY KEY,
    title        VARCHAR(500) NOT NULL,
    content_md   TEXT         NULL,
    content_html TEXT         NULL,
    cover_image  VARCHAR(500) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'draft',
    source       VARCHAR(100) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_article_status ON article(status);
CREATE INDEX IF NOT EXISTS idx_article_created_at ON article(created_at);

COMMENT ON TABLE article IS '文章表';
COMMENT ON COLUMN article.id IS '文章 ID';
COMMENT ON COLUMN article.title IS '文章标题';
COMMENT ON COLUMN article.content_md IS 'Markdown 原文';
COMMENT ON COLUMN article.content_html IS 'HTML 渲染内容';
COMMENT ON COLUMN article.status IS '状态: draft/generating/generated/published/failed';
COMMENT ON COLUMN article.source IS '文章来源标识';

CREATE TABLE IF NOT EXISTS article_image
(
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    alt_text VARCHAR(500) NOT NULL,
    heading_anchor VARCHAR(500),
    comfy_prompt_id VARCHAR(100),
    storage_key VARCHAR(500),
    content_type VARCHAR(100),
    inserted BOOLEAN NOT NULL DEFAULT FALSE,
    wechat_content_url VARCHAR(1000),
    wechat_thumb_media_id VARCHAR(500),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_article_image_article_id ON article_image(article_id);
CREATE INDEX IF NOT EXISTS idx_article_image_status ON article_image(status);

-- ============================================================
-- 1.1 资源图库
-- ============================================================
CREATE TABLE IF NOT EXISTS media_category
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_category_name_lower ON media_category (LOWER(name));

CREATE TABLE IF NOT EXISTS media_asset
(
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT REFERENCES media_category(id) ON DELETE SET NULL,
    article_image_id BIGINT UNIQUE REFERENCES article_image(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(20) NOT NULL,
    prompt TEXT,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_media_asset_category_id ON media_asset(category_id);
CREATE INDEX IF NOT EXISTS idx_media_asset_created_at ON media_asset(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_asset_source ON media_asset(source);

-- ============================================================
-- 2. 话题表
-- ============================================================
CREATE TABLE IF NOT EXISTS topic
(
    id BIGSERIAL PRIMARY KEY,
    title  VARCHAR(500) NOT NULL,
    source VARCHAR(100) NOT NULL,
    score  INT          NOT NULL DEFAULT 0,
    status VARCHAR(20)  NOT NULL DEFAULT 'pending',
    reason TEXT         NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_topic_source ON topic(source);
CREATE INDEX IF NOT EXISTS idx_topic_score ON topic(score);
CREATE INDEX IF NOT EXISTS idx_topic_status ON topic(status);

COMMENT ON TABLE topic IS '话题表';
COMMENT ON COLUMN topic.id IS '话题 ID';
COMMENT ON COLUMN topic.title IS '话题名称';
COMMENT ON COLUMN topic.source IS '来源: github/openhot/rss/dailyhot';
COMMENT ON COLUMN topic.score IS '热度评分';
COMMENT ON COLUMN topic.status IS '状态: pending/selected/done';
COMMENT ON COLUMN topic.reason IS '选题理由';

-- ============================================================
-- 3. 调研记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS research
(
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topic (id) ON DELETE CASCADE,
    summary  TEXT   NULL,
    raw_data JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_research_topic_id ON research(topic_id);

COMMENT ON TABLE research IS '调研记录表';
COMMENT ON COLUMN research.id IS '调研记录 ID';
COMMENT ON COLUMN research.topic_id IS '关联话题 ID';
COMMENT ON COLUMN research.summary IS '调研摘要';
COMMENT ON COLUMN research.raw_data IS '原始调研数据';

-- ============================================================
-- 4. 执行日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS execution_log
(
    id BIGSERIAL PRIMARY KEY,
    step       VARCHAR(100) NOT NULL,
    agent_name VARCHAR(100) NULL,
    input JSONB NULL,
    output JSONB NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'pending',
    error_msg  TEXT         NULL,
    article_id BIGINT       NULL REFERENCES article (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_el_step ON execution_log(step);
CREATE INDEX IF NOT EXISTS idx_el_status ON execution_log(status);
CREATE INDEX IF NOT EXISTS idx_el_article_id ON execution_log(article_id);
CREATE INDEX IF NOT EXISTS idx_el_created_at ON execution_log(created_at);

COMMENT ON TABLE execution_log IS '执行日志表（工作流审计）';
COMMENT ON COLUMN execution_log.id IS '日志 ID';
COMMENT ON COLUMN execution_log.step IS '步骤名称: topic/research/outline/writer/review/seo/publish';
COMMENT ON COLUMN execution_log.agent_name IS '智能体名称';
COMMENT ON COLUMN execution_log.input IS '步骤输入';
COMMENT ON COLUMN execution_log.output IS '步骤输出';
COMMENT ON COLUMN execution_log.status IS '状态: pending/running/success/failed';
COMMENT ON COLUMN execution_log.error_msg IS '错误信息';
COMMENT ON COLUMN execution_log.article_id IS '关联文章 ID';

-- ============================================================
-- 5. 知识块表（RAG + pgvector）
-- ============================================================
-- 设计参考: doc/DATABASE.md §3.5
-- 使用 pgvector VECTOR(1536) 存储 OpenAI ada-002 向量
CREATE TABLE IF NOT EXISTS knowledge_chunk
(
    id BIGSERIAL PRIMARY KEY,
    article_id   BIGINT       NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    chunk_index  INT          NOT NULL DEFAULT 0,
    title        VARCHAR(255) NULL,
    content      TEXT         NOT NULL,
    tokens_count INT          NOT NULL DEFAULT 0,
    embedding VECTOR(1536) NULL,
    metadata_json JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kc_article_id ON knowledge_chunk(article_id);
CREATE INDEX IF NOT EXISTS idx_kc_chunk_index ON knowledge_chunk(chunk_index);

-- HNSW 向量索引（余弦相似度）
-- m: 连接数，ef_construction: 构建时的搜索范围
-- 值越大索引质量越高，但构建越慢
CREATE INDEX IF NOT EXISTS idx_kc_embedding_hnsw ON knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

COMMENT ON TABLE knowledge_chunk IS '知识块表（RAG 分块存储）';
COMMENT ON COLUMN knowledge_chunk.id IS '知识块 ID';
COMMENT ON COLUMN knowledge_chunk.article_id IS '来源文章 ID';
COMMENT ON COLUMN knowledge_chunk.chunk_index IS '块在文章中的序号';
COMMENT ON COLUMN knowledge_chunk.title IS '所在章节标题';
COMMENT ON COLUMN knowledge_chunk.content IS '文本块内容';
COMMENT ON COLUMN knowledge_chunk.tokens_count IS '估算 token 数';
COMMENT ON COLUMN knowledge_chunk.embedding IS '向量嵌入 (OpenAI ada-002: 1536维)';
COMMENT ON COLUMN knowledge_chunk.metadata_json IS '元数据: {tags, codeLanguage, hasCode, articleTitle}';

-- ============================================================
-- 6. 智能体调用日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_log
(
    id BIGSERIAL PRIMARY KEY,
    log_id     BIGINT       NOT NULL REFERENCES execution_log (id) ON DELETE CASCADE,
    agent_name VARCHAR(100) NOT NULL,
    prompt     TEXT         NULL,
    response   TEXT         NULL,
    tokens_in  INT                   DEFAULT 0,
    tokens_out INT                   DEFAULT 0,
    latency_ms INT                   DEFAULT 0,
    status     VARCHAR(20)  NOT NULL DEFAULT 'success',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_al_log_id ON agent_log(log_id);
CREATE INDEX IF NOT EXISTS idx_al_agent_name ON agent_log(agent_name);

COMMENT ON TABLE agent_log IS '智能体调用日志表';
COMMENT ON COLUMN agent_log.id IS '记录 ID';
COMMENT ON COLUMN agent_log.log_id IS '关联 execution_log ID';
COMMENT ON COLUMN agent_log.agent_name IS '智能体名称: outline/writer/review/seo';
COMMENT ON COLUMN agent_log.prompt IS '使用的提示词';
COMMENT ON COLUMN agent_log.response IS 'LLM 返回结果';
COMMENT ON COLUMN agent_log.tokens_in IS '输入 token 数';
COMMENT ON COLUMN agent_log.tokens_out IS '输出 token 数';
COMMENT ON COLUMN agent_log.latency_ms IS '调用耗时（毫秒）';
COMMENT ON COLUMN agent_log.status IS '状态: success/failed';

-- ============================================================
-- 7. 自动更新时间戳触发器
-- ============================================================
CREATE OR
REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql AS
'
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
';

DROP TRIGGER IF EXISTS trigger_article_updated_at ON article;
CREATE TRIGGER trigger_article_updated_at
    BEFORE UPDATE
    ON article
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trigger_knowledge_chunk_updated_at ON knowledge_chunk;
CREATE TRIGGER trigger_knowledge_chunk_updated_at
    BEFORE UPDATE
    ON knowledge_chunk
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 8. 向量搜索函数
-- ============================================================

-- 余弦相似度搜索（推荐用于 RAG 检索）
CREATE
OR
REPLACE FUNCTION search_knowledge(
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
LANGUAGE plpgsql AS
'
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
';

COMMENT ON FUNCTION search_knowledge IS 'RAG 向量相似度搜索函数';


CREATE TABLE IF NOT EXISTS system_config
(
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
