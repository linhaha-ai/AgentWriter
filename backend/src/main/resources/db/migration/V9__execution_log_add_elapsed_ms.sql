-- 执行日志表新增"步骤耗时"字段，用于生成日志页面展示每个环节的执行耗时
ALTER TABLE execution_log ADD COLUMN IF NOT EXISTS elapsed_ms BIGINT;
