@echo off
setlocal
set "ROOT=%~dp0.."
set "COMPOSE=%ROOT%\docker\docker-compose.yml"

call "%~dp0apply-system-config-migration.bat"
if errorlevel 1 exit /b 1
call "%~dp0apply-system-config-migration.bat"
if errorlevel 1 exit /b 1

docker compose -f "%COMPOSE%" exec -T postgres psql -v ON_ERROR_STOP=1 -U agentwriter -d agentwriter -c "SELECT to_regclass('public.system_config') AS system_config_table;"
exit /b %errorlevel%
