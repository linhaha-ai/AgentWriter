@echo off
chcp 65001 >nul
echo ========================================
echo  AgentWriter - 构建
echo ========================================
echo.

REM 构建后端
echo [1/2] 构建后端...
cd /d "%~dp0..\backend"
call ..\mvnw clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] 后端构建失败
    pause
    exit /b 1
)
echo [OK] 后端构建完成
echo        JAR: backend\target\agentwriter-*.jar

REM 构建前端
echo.
echo [2/2] 构建前端...
cd /d "%~dp0..\frontend"
call npx vite build
if %errorlevel% neq 0 (
    echo [ERROR] 前端构建失败
    pause
    exit /b 1
)
echo [OK] 前端构建完成
echo        dist: frontend\dist\

echo.
echo ========================================
echo  构建成功！
echo ========================================
echo.
pause
