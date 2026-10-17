-- ============================================================
-- V5: execution_log 审计字段 input/output 由 JSON 改为 TEXT
--
-- 这两个字段实际存储各工作流步骤的自由文本（Markdown、调试文本等），
-- 并非合法 JSON。原 JSON 类型在写入 research 等步骤的 Markdown 输出时
-- 会导致 PostgreSQL 报错 "invalid input syntax for type json"。
--
-- 使用 information_schema 条件判断：仅当列存在且类型为 json/jsonb 时
-- 才执行转换，保证在全新库（表尚未由 Hibernate 创建）上也可安全执行。
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'execution_log'
          AND column_name = 'input'
          AND data_type IN ('json', 'jsonb')
    ) THEN
        ALTER TABLE execution_log ALTER COLUMN input TYPE TEXT USING input::text;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'execution_log'
          AND column_name = 'output'
          AND data_type IN ('json', 'jsonb')
    ) THEN
        ALTER TABLE execution_log ALTER COLUMN output TYPE TEXT USING output::text;
    END IF;
END $$;
