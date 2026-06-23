@echo off
chcp 65001 >nul
cd /d "%~dp0"

findstr /C:"please-change-me" .env >nul 2>&1
if not errorlevel 1 (
  echo !! 检测到 .env 仍是默认密钥, 请先编辑 .env 修改 DB_PASSWORD 和 JWT_SECRET 后重试.
  pause
  exit /b 1
)

set APP_PORT=80
for /f "tokens=2 delims==" %%a in ('findstr /B "APP_PORT=" .env') do set APP_PORT=%%a

echo ==^> 加载 Docker 镜像 (首次较慢, 请耐心等待)...
docker load -i images\victor-images.tar
if errorlevel 1 ( echo 镜像加载失败 & pause & exit /b 1 )

echo ==^> 启动 Victor...
docker compose up -d
if errorlevel 1 ( echo 启动失败 & pause & exit /b 1 )

echo.
echo ==^> 启动完成! 浏览器访问: http://localhost:%APP_PORT%
echo     默认管理员账号: admin / admin123
echo     查看日志: docker compose logs -f
echo     停止服务: docker compose down
pause