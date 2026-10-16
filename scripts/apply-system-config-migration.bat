@echo off
setlocal
set "ROOT=%~dp0.."
set "COMPOSE=%ROOT%\docker\docker-compose.yml"


if not exist "%MIGRATION%" (
    echo [ERROR] Migration file not found: %MIGRATION%
    exit /b 1
)

docker compose -f "%COMPOSE%" up -d postgres
if errorlevel 1 exit /b 1

set /a ATTEMPTS=0
:wait_for_postgres
docker compose -f "%COMPOSE%" exec -T postgres pg_isready -U agentwriter >nul 2>&1
if not errorlevel 1 goto apply_migration
set /a ATTEMPTS+=1
if %ATTEMPTS% GEQ 30 (
    echo [ERROR] PostgreSQL did not become ready within 30 seconds
    exit /b 1
)
timeout /t 1 /nobreak >nul
goto wait_for_postgres

:apply_migration
type "%MIGRATION%" | docker compose -f "%COMPOSE%" exec -T postgres psql -v ON_ERROR_STOP=1 -U agentwriter -d agentwriter
exit /b %errorlevel%
