# AgentWriter 前端

> AgentWriter 的运营仪表盘，基于 Vue 3 + Naive UI + TypeScript。

## 技术栈

- **框架**: Vue 3 + TypeScript
- **UI 组件库**: Naive UI（暗黑主题）
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **构建工具**: Vite

## 快速开始

```bash
# 安装依赖
pnpm install

# 启动开发服务器（端口 3000）
pnpm dev

# 生产构建
pnpm build

# 预览构建结果
pnpm preview
```

## 目录结构

```
src/
├── api/          # API 接口层（含 Mock 数据）
├── layouts/      # 页面布局组件
├── router/       # 路由配置
├── stores/       # Pinia 状态管理
├── types/        # TypeScript 类型定义
└── views/        # 页面视图
    ├── Dashboard.vue   # 运营概览
    ├── Articles.vue    # 文章管理
    ├── Topics.vue      # 热点话题
    └── Settings.vue    # 系统设置
```
