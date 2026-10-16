# Docker 开发环境

本目录包含 AgentWriter 本地开发依赖的 Docker Compose 配置。

## 服务列表

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL | `ankane/pgvector` | 5432 | 支持 pgvector 的数据库 |

## 快速启动

```bash
# 后台启动所有服务
docker compose up -d

# 查看日志
docker compose logs -f

# 停止所有服务
docker compose down

# 停止并删除数据卷（会销毁数据）
docker compose down -v
```

## 配置说明

环境变量定义在 `.env` 文件中，已预设合理的默认值：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `POSTGRES_DB` | `agentwriter` | 数据库名称 |
| `POSTGRES_USER` | `agentwriter` | 数据库用户 |
| `POSTGRES_PASSWORD` | `agentwriter_secret` | 数据库密码 |

如需覆盖默认值，复制 `.env` 为 `.env.local` 并编辑：

```bash
cp .env .env.local
# 编辑 .env.local 修改为你需要的值
```

## 应用配置

确保后端模块的 `application.yml` 中的数据库配置与之对应：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agentwriter
    username: agentwriter
    password: agentwriter_secret
```

## 健康检查

```bash
# 检查 PostgreSQL 是否就绪
docker compose exec postgres pg_isready -U agentwriter
```
