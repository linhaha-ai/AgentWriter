@echo off
chcp 65001 >nul
echo ========================================
echo  AgentWriter - 项目初始化
echo ========================================
echo.

REM 检查 Java
echo [1/6] 检查 Java 21...
java -version 2>&1 | findstr "21." >nul
if %errorlevel% neq 0 (
    echo [WARN] 未检测到 Java 21，请确保已安装 JDK 21
    echo         下载: https://adoptium.net/
) else (
    echo [OK] Java 21 已安装
)

echo.
echo [2/6] 检查 Node.js...
node -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] 请安装 Node.js 18+
    echo          下载: https://nodejs.org/
    pause
    exit /b 1
) else (
    for /f "tokens=1 delims=v" %%a in ('node -v') do set NODE_VER=%%a
    echo [OK] Node.js %NODE_VER% 已安装
)

echo.
echo [3/6] 安装前端依赖...
cd /d "%~dp0..\frontend"
if not exist "node_modules\" (
    npm install
    if %errorlevel% neq 0 (
        echo [ERROR] 前端依赖安装失败
        pause
        exit /b 1
    )
    echo [OK] 前端依赖已安装
) else (
    echo [OK] 前端依赖已存在（跳过）
)

echo.
echo [4/6] 检查 Maven Wrapper...
cd /d "%~dp0..\backend"
if not exist "..\mvnw" (
    echo [INFO] 初始化 Maven Wrapper...
    cd /d "%~dp0.."
    mvn -N wrapper:wrapper -Dmaven=3.9.6
) else (
    echo [OK] Maven Wrapper 已存在
)

echo.
echo [5/6] 配置环境变量...
set "FILE=%~dp0..\backend\.env"
if not exist "%FILE%" (
    echo # AgentWriter 环境变量配置 > "%FILE%"
    echo DB_PASSWORD=agentwriter_secret >> "%FILE%"
    echo AGNES_API_KEY= >> "%FILE%"
    echo WECHAT_APP_ID= >> "%FILE%"
    echo WECHAT_APP_SECRET= >> "%FILE%"
    echo WECHAT_THUMB_MEDIA_ID= >> "%FILE%"
    echo [WARN] 已生成 .env 模板，请编辑 %FILE% 填入 API Key
) else (
    echo [OK] .env 已存在
)

echo.
echo [6/6] 应用 system_config 数据库迁移...
call "%~dp0apply-system-config-migration.bat"
if %errorlevel% neq 0 (
    echo [ERROR] system_config 数据库迁移失败
    pause
    exit /b 1
)
echo [OK] system_config 数据库迁移已应用（可重复执行）

echo.
echo ========================================
echo  初始化完成！
echo  运行 dev-start.bat 启动开发环境
echo ========================================
echo.
pause
