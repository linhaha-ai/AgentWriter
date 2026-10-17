-- ============================================================
-- V8: 放宽 media_asset 旧表约束，适配资源图库新语义
-- 旧 schema（V1 全量建库）将 storage_key/name/content_type/file_size 设为 NOT NULL，
-- 图库素材（保存链接/上传）并非都具备这些属性，改为可空。
-- ============================================================

ALTER TABLE media_asset ALTER COLUMN name DROP NOT NULL;
ALTER TABLE media_asset ALTER COLUMN storage_key DROP NOT NULL;
ALTER TABLE media_asset ALTER COLUMN content_type DROP NOT NULL;
ALTER TABLE media_asset ALTER COLUMN file_size DROP NOT NULL;
