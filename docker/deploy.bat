@echo off
chcp 65001 >nul
cd /d "%~dp0"

findstr /C:"please-change-me" .env >nul 2>&1
if not errorlevel 1 (
  echo !! 检测到 .env 仍是默认密钥, 请先编辑 .env 修改 DB_PASSWORD 和 JWT_SECRET 后重试.
  pause & exit /b 1
)

if not exist "app\backend\manifest.json" (
  echo !! 未找到 app\backend\manifest.json, 请确认部署包完整。
  pause & exit /b 1
)

set APP_VER=
set REQ_BASE=
for /f "tokens=2 delims=:" %%a in ('findstr /C:"\"appVersion\"" app\backend\manifest.json') do (
  for /f "tokens=1 delims=," %%b in ("%%a") do set APP_VER=%%b
)
for /f "tokens=2 delims=:" %%a in ('findstr /C:"\"requiresBase\"" app\backend\manifest.json') do (
  for /f "tokens=1 delims=," %%b in ("%%a") do set REQ_BASE=%%b
)
REM 去除首尾空格与引号
set APP_VER=%APP_VER:"=%
set APP_VER=%APP_VER: =%
set REQ_BASE=%REQ_BASE:"=%
set REQ_BASE=%REQ_BASE: =%
echo ==^> 应用版本: v%APP_VER%  依赖基础镜像: victor-base:%REQ_BASE%

echo ==^> 加载 Docker 镜像 (首次较慢, 请耐心等待)...
if exist images (
  for %%f in (images\*.tar) do (
    echo     load: %%f
    docker load -i "%%f"
    if errorlevel 1 ( echo 镜像加载失败: %%f & pause & exit /b 1 )
  )
)

docker image inspect victor-base:%REQ_BASE% >nul 2>&1
if errorlevel 1 (
  echo ==============================================================
  echo !! 缺少基础镜像 victor-base:%REQ_BASE%
  echo    本次发布包未包含该基础镜像, 请从发布 victor-base:%REQ_BASE%
  echo    的那个 Release 下载 base 包, docker load 后重试。
  echo ==============================================================
  pause & exit /b 1
)

docker image inspect victor-db:%APP_VER% >nul 2>&1
if errorlevel 1 (
  echo !! 缺少数据库镜像 victor-db:%APP_VER%, 请确认 images 下 db 镜像已加载。
  pause & exit /b 1
)

echo ==^> 启动 Victor...
set VICTOR_BASE_VERSION=%REQ_BASE%
set VICTOR_APP_VERSION=%APP_VER%
docker compose up -d
if errorlevel 1 ( echo 启动失败 & pause & exit /b 1 )

set APP_PORT=80
for /f "tokens=2 delims==" %%a in ('findstr /B "APP_PORT=" .env') do set APP_PORT=%%a

echo.
echo ==^> 启动完成! 浏览器访问: http://localhost:%APP_PORT%
echo     默认管理员账号: admin / admin123
echo     查看日志: docker compose logs -f
echo     停止服务: docker compose down
echo     升级数据库: upgrade-db.bat (仅版本间 schema 变化时执行)
pause
