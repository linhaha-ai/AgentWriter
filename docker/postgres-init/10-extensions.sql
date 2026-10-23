-- PostgreSQL 初始化脚本：空库首次启动时自动执行（docker-entrypoint-initdb.d）
-- 启用 pgvector 扩展：应用迁移 V1 中 VECTOR(1536) 列依赖该类型
CREATE EXTENSION IF NOT EXISTS vector;
