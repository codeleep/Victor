@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist backups mkdir backups

REM 读取 .env
set DB_NAME=victor
set DB_USERNAME=postgres
if exist .env (
  for /f "tokens=1,2 delims==" %%a in (.env) do (
    if /i "%%a"=="DB_NAME" set DB_NAME=%%b
    if /i "%%a"=="DB_USERNAME" set DB_USERNAME=%%b
  )
)

for /f "tokens=2 delims==" %%t in ('wmic os get localdatetime /value ^| find "="') do set TSRAW=%%t
set TS=%TSRAW:~0,8%-%TSRAW:~8,6%
set FULL=backups\backup-full-%TS%.sql
set DATA=backups\backup-data-%TS%.sql
set WARN=backups\upgrade-warnings-%TS%.log

echo ==^> [1/6] 停止 victor-app ...
docker compose stop victor-app >nul 2>&1

echo ==^> [2/6] 备份数据库 -^> %FULL% / %DATA% ...
docker compose exec -T victor-postgres pg_dump -U %DB_USERNAME% -d %DB_NAME% > "%FULL%" 2>>"%WARN%"
if errorlevel 1 (
  echo !! 全量备份失败, 已中止。见 %WARN%
  docker compose start victor-app >nul 2>&1
  pause & exit /b 1
)
docker compose exec -T victor-postgres pg_dump --data-only --column-inserts -U %DB_USERNAME% -d %DB_NAME% > "%DATA%" 2>>"%WARN%"
if errorlevel 1 (
  echo !! 数据备份失败, 已中止。见 %WARN%
  docker compose start victor-app >nul 2>&1
  pause & exit /b 1
)

echo ==^> [3/6] 删除并重建数据库 %DB_NAME% ...
docker compose exec -T victor-postgres dropdb -U %DB_USERNAME% --if-exists --force %DB_NAME% 2>>"%WARN%"
docker compose exec -T victor-postgres createdb -U %DB_USERNAME% %DB_NAME% 2>>"%WARN%"

echo ==^> [4/6] 执行新版本 schema.sql + data.sql (逐条容错) ...
docker compose exec -T victor-postgres psql -v ON_ERROR_STOP=0 -U %DB_USERNAME% -d %DB_NAME% -f /docker-entrypoint-initdb.d/01-schema.sql 2>>"%WARN%"
docker compose exec -T victor-postgres psql -v ON_ERROR_STOP=0 -U %DB_USERNAME% -d %DB_NAME% -f /docker-entrypoint-initdb.d/02-data.sql 2>>"%WARN%"

echo ==^> [5/6] 回灌旧数据 (逐条容错) ...
docker compose exec -T victor-postgres psql -v ON_ERROR_STOP=0 -U %DB_USERNAME% -d %DB_NAME% < "%DATA%" 2>>"%WARN%"

echo ==^> [6/6] 重启 victor-app ...
docker compose start victor-app >nul 2>&1
if errorlevel 1 docker compose up -d victor-app

echo.
echo ==^> 升级完成。
echo     备份目录: backups\
echo     警告日志: %WARN%
echo     全量快照: %FULL%
pause
