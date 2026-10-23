# 前端镜像：多阶段构建（Node 编译 → Nginx 托管静态资源）
# 构建上下文为 frontend/ 目录
FROM node:22-alpine AS build
WORKDIR /app

# 使用国内 npm 镜像加速；先复制 package.json 以利用依赖层缓存
# （仓库未提交 lockfile，故使用 npm install 解析依赖）
RUN npm config set registry https://registry.npmmirror.com
COPY package.json ./
RUN npm install --no-audit --no-fund

# 复制源码并执行完整构建（vue-tsc 类型检查 + vite 打包）
COPY . .
RUN npm run build

# 运行阶段：Nginx 托管构建产物并反代 API（配置见 docker/nginx.conf）
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
