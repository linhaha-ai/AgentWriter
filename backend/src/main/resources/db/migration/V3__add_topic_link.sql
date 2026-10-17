-- ============================================================
-- V3: 话题表新增 link 字段，用于存储原文/GitHub 链接
-- （修复原 V2 表名错误 topics -> topic，并幂等化）
-- ============================================================

ALTER TABLE topic ADD COLUMN IF NOT EXISTS link VARCHAR(1000);

COMMENT ON COLUMN topic.link IS '原文链接或 GitHub 仓库链接';
