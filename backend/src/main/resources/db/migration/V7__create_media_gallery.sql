-- ============================================================
-- V7: 资源图库（图片素材 + 分类）
-- 与 Flyway 基线（V1 全量建库）保持幂等：IF NOT EXISTS
-- ============================================================

-- 图库分类表
CREATE TABLE IF NOT EXISTS media_category (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    description VARCHAR(500),
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);

-- 图库图片素材表
CREATE TABLE IF NOT EXISTS media_asset (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(200),
    url          TEXT          NOT NULL,
    prompt       TEXT,
    source       VARCHAR(20)   NOT NULL DEFAULT 'URL',
    category_id  BIGINT        REFERENCES media_category (id) ON DELETE SET NULL,
    tags         TEXT          NOT NULL DEFAULT '',
    width        INT,
    height       INT,
    file_size    BIGINT,
    content_type VARCHAR(100),
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_media_asset_category ON media_asset (category_id);
CREATE INDEX IF NOT EXISTS idx_media_asset_created ON media_asset (created_at DESC);
