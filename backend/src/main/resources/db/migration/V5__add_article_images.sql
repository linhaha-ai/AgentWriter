-- ============================================================
-- V5: 文章图片生成任务与发布素材记录
-- （幂等化：可在 Hibernate 已建表的库上安全重复执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS article_image (
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
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_article_image_article_id ON article_image(article_id);
CREATE INDEX IF NOT EXISTS idx_article_image_status ON article_image(status);

COMMENT ON TABLE article_image IS '文章生成图片及其外部发布素材状态';
