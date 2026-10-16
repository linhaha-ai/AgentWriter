-- ============================================================
-- V2: 话题表新增 link 字段，用于存储原文/GitHub 链接
-- ============================================================

ALTER TABLE topics ADD COLUMN link VARCHAR(1000);

COMMENT ON COLUMN topics.link IS '原文链接或 GitHub 仓库链接';
