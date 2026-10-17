-- ============================================================
-- V2: 文章创建流程改造 - 新增模板、风格表及文章表扩展
-- （幂等化：可在 Hibernate 已建表的库上安全重复执行）
-- ============================================================

-- 1. 创建文章模板表
CREATE TABLE IF NOT EXISTS article_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    title_template VARCHAR(500),
    outline_template TEXT,
    cover_rule VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE article_template IS '文章模板';
COMMENT ON COLUMN article_template.name IS '模板名称';
COMMENT ON COLUMN article_template.description IS '模板描述';
COMMENT ON COLUMN article_template.title_template IS '标题模板，支持 {topic} 变量';
COMMENT ON COLUMN article_template.outline_template IS '段落大纲模板（JSON 格式存储章节结构）';
COMMENT ON COLUMN article_template.cover_rule IS '封面图规则';
COMMENT ON COLUMN article_template.enabled IS '是否启用';

-- 2. 创建写作风格表
CREATE TABLE IF NOT EXISTS writer_style (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    tone_prompt TEXT,
    features VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE writer_style IS '写作风格';
COMMENT ON COLUMN writer_style.name IS '风格名称';
COMMENT ON COLUMN writer_style.description IS '风格描述';
COMMENT ON COLUMN writer_style.tone_prompt IS '语气提示词，拼入 WriterAgent system prompt';
COMMENT ON COLUMN writer_style.features IS '风格特征标签（如：技术深度、通俗易懂）';
COMMENT ON COLUMN writer_style.enabled IS '是否启用';

-- 3. 文章表新增字段（幂等：列已存在则跳过）
ALTER TABLE article
    ADD COLUMN IF NOT EXISTS template_id BIGINT REFERENCES article_template(id),
    ADD COLUMN IF NOT EXISTS style_id BIGINT REFERENCES writer_style(id),
    ADD COLUMN IF NOT EXISTS generation_progress TEXT;

COMMENT ON COLUMN article.template_id IS '关联的文章模板';
COMMENT ON COLUMN article.style_id IS '关联的写作风格';
COMMENT ON COLUMN article.generation_progress IS '生成进度 JSON，如 {"currentStep":"writer","totalSteps":5,"progress":40}';

-- 4. 索引（幂等）
CREATE INDEX IF NOT EXISTS idx_article_template_id ON article(template_id);
CREATE INDEX IF NOT EXISTS idx_article_style_id ON article(style_id);
