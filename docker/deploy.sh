#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"

if grep -q "please-change-me" .env 2>/dev/null; then
  echo "!! 检测到 .env 仍是默认密钥, 请先编辑 .env 修改 DB_PASSWORD 和 JWT_SECRET 后重试."
  exit 1
fi

APP_PORT=$(grep -E '^APP_PORT=' .env 2>/dev/null | cut -d= -f2-)
APP_PORT="${APP_PORT:-80}"

echo "==> 加载 Docker 镜像 (首次较慢, 请耐心等待)..."
docker load -i images/victor-images.tar

echo "==> 启动 Victor..."
docker compose up -d

echo ""
echo "==> 启动完成! 浏览器访问: http://localhost:${APP_PORT}"
echo "    默认管理员账号: admin / admin123"
echo "    查看日志: docker compose logs -f"
echo "    停止服务: docker compose down"