@echo off
chcp 65001 >nul
echo ========================================
echo  AgentWriter - 开发环境启动
echo ========================================
echo.

REM 检查 PostgreSQL
echo [1/3] 检查 PostgreSQL 连接...
pg_isready -h localhost -p 5432 >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] PostgreSQL 未运行，尝试启动 Docker...
    cd /d "%~dp0..\docker"
    docker-compose up -d
    if %errorlevel% neq 0 (
        echo [ERROR] 无法启动 PostgreSQL，请手动启动 Docker Desktop
        pause
        exit /b 1
    )
    echo [OK] Docker PostgreSQL 已启动
    timeout /t 3 /nobreak >nul
) else (
    echo [OK] PostgreSQL 已连接
)

echo.
echo [2/3] 启动后端 (Spring Boot)...
cd /d "%~dp0..\backend"
start "AgentWriter Backend" cmd /c "..\mvnw spring-boot:run -Dspring-boot.run.profiles=dev"

echo.
echo [3/3] 启动前端 (Vite)...
cd /d "%~dp0..\frontend"
start "AgentWriter Frontend" cmd /c "npx vite --port 5173"

echo.
echo ========================================
echo  后端: http://localhost:8080
echo  前端: http://localhost:5173
echo ========================================
echo.
pause
