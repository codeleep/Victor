#!/usr/bin/env sh
# =============================================================================
# Victor 部署脚本 (离线包目录下运行)
#
# 目录结构:
#   app/backend/manifest.json   appVersion + requiresBase
#   images/victor-base-*.tar    base 镜像 (仅依赖变化时才带)
#   images/victor-db-*.tar       db 镜像 (每次发版都带)
#
# 启动前校验: victor-base:<requiresBase> 镜像必须存在; 缺失则提示去对应
# release 下载 base 包。
# =============================================================================
set -e
cd "$(dirname "$0")"

if grep -q "please-change-me" .env 2>/dev/null; then
  echo "!! 检测到 .env 仍是默认密钥, 请先编辑 .env 修改 DB_PASSWORD 和 JWT_SECRET 后重试."
  exit 1
fi

MANIFEST="app/backend/manifest.json"
if [ ! -f "$MANIFEST" ]; then
  echo "!! 未找到 $MANIFEST, 请确认部署包完整。"
  exit 1
fi
APP_VER=$(sed -n 's/.*"appVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$MANIFEST" | head -n1)
REQ_BASE=$(sed -n 's/.*"requiresBase"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$MANIFEST" | head -n1)
echo "==> 应用版本: v${APP_VER}  依赖基础镜像: victor-base:${REQ_BASE}"

echo "==> 加载 Docker 镜像 (首次较慢, 请耐心等待)..."
if [ -d images ]; then
  for tar in images/*.tar; do
    [ -e "$tar" ] || continue
    echo "    load: $tar"
    docker load -i "$tar"
  done
fi

# 校验 base 镜像
if ! docker image inspect "victor-base:${REQ_BASE}" >/dev/null 2>&1; then
  echo "=============================================================="
  echo "!! 缺少基础镜像 victor-base:${REQ_BASE}"
  echo "   本次发布包未包含该基础镜像 (说明依赖未变化, 复用旧基础镜像)。"
  echo "   请从发布 victor-base:${REQ_BASE} 的那个 Release 下载对应的"
  echo "   base 包 (victor-base-${REQ_BASE}-*.zip), 解压后 docker load 后重试。"
  echo "=============================================================="
  exit 1
fi

# 校验 db 镜像
if ! docker image inspect "victor-db:${APP_VER}" >/dev/null 2>&1; then
  echo "!! 缺少数据库镜像 victor-db:${APP_VER}, 请确认 images/ 下的 db 镜像已加载。"
  exit 1
fi

echo "==> 启动 Victor..."
export VICTOR_BASE_VERSION="$REQ_BASE"
export VICTOR_APP_VERSION="$APP_VER"
docker compose up -d

APP_PORT=$(grep -E '^APP_PORT=' .env 2>/dev/null | cut -d= -f2-)
APP_PORT="${APP_PORT:-80}"
echo ""
echo "==> 启动完成! 浏览器访问: http://localhost:${APP_PORT}"
echo "    默认管理员账号: admin / admin123"
echo "    查看日志: docker compose logs -f"
echo "    停止服务: docker compose down"
echo "    升级数据库: sh upgrade-db.sh (仅版本间 schema 变化时执行)"
