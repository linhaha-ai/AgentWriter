# Docker 全栈运行环境

本目录包含 AgentWriter 一键启动所需的 Docker Compose 配置与镜像构建文件。

## 服务列表

| 服务 | 镜像 / 构建方式 | 宿主端口 | 用途 |
|------|-----------------|----------|------|
| postgres | `pgvector/pgvector:0.8.6-pg16-bookworm` | 5432 | PostgreSQL 16 + pgvector |
| backend | `docker/Dockerfile.backend`（Maven → JRE 21 多阶段） | 不暴露 | Spring Boot 后端（8080 仅 compose 网内可达） |
| frontend | `frontend/Dockerfile`（Node → Nginx 多阶段） | 3000 | 托管前端静态资源并反代 `/api`、`/media` |

## 一键启动

```bash
# 仓库根目录（推荐）
docker compose -f docker/docker-compose.yml up -d --build

# 或在本目录
docker compose up -d --build
```

首次启动流程：构建后端镜像（Maven 走阿里云镜像加速）与前端镜像（npm 走 npmmirror 加速）→
初始化数据库（空库自动执行 `postgres-init/` 创建 pgvector 扩展）→ 后端启动时 Flyway 自动执行
V1~V9 建表 → 各服务健康检查通过 → 访问 http://localhost:3000。

## 常用命令

```bash
# 查看状态与健康检查
docker compose -f docker/docker-compose.yml ps

# 跟踪日志（可换 frontend / postgres）
docker compose -f docker/docker-compose.yml logs -f backend

# 停止（保留数据）
docker compose -f docker/docker-compose.yml down

# 停止并删除数据卷（清空数据库与生成图片，不可恢复）
docker compose -f docker/docker-compose.yml down -v

# 修改代码后仅重建某个服务
docker compose -f docker/docker-compose.yml up -d --build backend
```

## 配置覆盖（.env）

复制 `.env.example` 为 `.env`（已被 .gitignore 忽略）后按需修改，重新 `up` 生效：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FRONTEND_HOST_PORT` | 3000 | 前端访问端口 |
| `POSTGRES_HOST_PORT` | 5432 | 数据库宿主机端口 |
| `POSTGRES_DB` | agentwriter | 数据库名（postgres 与后端共用，需成对修改） |
| `POSTGRES_USER` | agentwriter | 数据库用户 |
| `POSTGRES_PASSWORD` | agentwriter_secret | 数据库密码 |
| `TOPIC_PROXY_HOST` | 空（直连） | 外网代理地址（可选） |
| `TOPIC_PROXY_PORT` | 0 | 外网代理端口（可选） |

### 外网代理说明

容器内默认**直连**外网（已实测容器内可直达 github.com、s.jina.ai、apihub.agnes-ai.com）。
仅监听 `127.0.0.1` 的宿主代理（如 Clash 默认配置的 `127.0.0.1:7890`）**容器无法访问**；
如确需容器走代理，请先开启代理的 `allow-lan`（监听 `0.0.0.0`），再把 `TOPIC_PROXY_HOST`
设为宿主局域网 IP。

## 数据持久化

| 数据卷（项目前缀 `agentwriter_`） | 内容 |
|----------------------------------|------|
| `pgdata` | 数据库数据 |
| `backend-data` | 生成图片等运行时数据（容器内 `/app/data`） |

数据库 schema 完全由 Flyway 管理（compose 以 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`
禁止 Hibernate 抢先建表），迁移脚本位于 `backend/src/main/resources/db/migration/`。

## 与本地开发环境共存

- 本地开发用的库容器（`agentwriter-postgres`，宿主 5433，数据卷 `agentwriter-pgdata`）
  **不受本 compose 影响**：本项目固定项目名 `agentwriter`，使用独立数据卷 `agentwriter_pgdata`
  与独立容器（`agentwriter-postgres-1`）。
- 本地 `mvn spring-boot:run`（8080）或 Vite dev server（3000）运行期间，如与本 compose 的
  端口冲突，请修改 `.env` 中的端口映射或先停止本地服务。

## 文件结构

```
docker/
├── docker-compose.yml     # 全栈编排（postgres + backend + frontend）
├── .env.example           # 可覆盖配置示例（复制为 .env 使用）
├── Dockerfile.backend     # 后端多阶段构建
├── maven-settings.xml     # Maven 阿里云镜像加速
├── postgres-init/         # 数据库空库初始化脚本（pgvector 扩展）
└── README.md              # 本说明

frontend/
├── Dockerfile             # 前端多阶段构建（Node 构建 → Nginx 托管）
└── nginx.conf             # Nginx 站点配置（SPA 回退、/api 与 /media 反代、SSE 支持）
```

## 故障排查

```bash
# 启动失败多为端口被占用：修改 docker/.env 中的端口后重新 up
# 后端反复重启：查看日志定位启动异常
docker compose -f docker/docker-compose.yml logs backend

# 数据库初始化异常需要重置时（会清空 Docker 全栈数据，不影响本地开发库）
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d --build
```