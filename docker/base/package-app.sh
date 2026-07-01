#!/usr/bin/env bash
# =============================================================================
# 打包 app 挂载目录 (前端 dist + 后端 modules + manifest)。
# 不含第三方 lib (lib 由 base 镜像提供, CI 单独构建用于 diff/打镜像)。
#
# 用法:
#   package-app.sh <app_version> <requires_base> [out_dir]
#
# 产物结构:
#   <out>/app/frontend/              vite 构建产物
#   <out>/app/backend/modules/       4 个内部模块 jar
#   <out>/app/backend/config/        可选配置覆盖 (空目录)
#   <out>/app/backend/manifest.json  {appVersion, requiresBase, modules}
#
# 脚本须在仓库根目录运行 (或通过相对路径定位, 脚本内已 cd 到 REPO_ROOT)。
# =============================================================================
set -euo pipefail
PY=python3; command -v python3 >/dev/null 2>&1 || PY=python

APP_VERSION="${1:?usage: package-app.sh <app_version> <requires_base> [out_dir]}"
REQUIRES_BASE="${2:?usage: package-app.sh <app_version> <requires_base> [out_dir]}"
OUT="${3:-./build}"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

echo "==> [1/4] 构建前端 (APP_VERSION=${APP_VERSION}) ..."
(cd frontend && APP_VERSION="$APP_VERSION" npm ci && APP_VERSION="$APP_VERSION" npm run build)

echo "==> [2/4] 构建后端模块 (普通 jar, 不 repackage) ..."
(cd backend && mvn -pl victor-web -am clean package -DskipTests -B)

echo "==> [3/4] 组装 app 目录 -> ${OUT}/app ..."
rm -rf "${OUT}/app"
mkdir -p "${OUT}/app/frontend" "${OUT}/app/backend/modules" "${OUT}/app/backend/config"
cp -r frontend/dist/. "${OUT}/app/frontend/"
cp backend/victor-common/target/victor-common-*.jar "${OUT}/app/backend/modules/"
cp backend/victor-infra/target/victor-infra-*.jar   "${OUT}/app/backend/modules/"
cp backend/victor-core/target/victor-core-*.jar     "${OUT}/app/backend/modules/"
cp backend/victor-web/target/victor-web-*.jar       "${OUT}/app/backend/modules/"

echo "==> [4/4] 生成 manifest.json (requiresBase=${REQUIRES_BASE}) ..."
"$PY" - "$APP_VERSION" "$REQUIRES_BASE" "${OUT}/app/backend/modules" > "${OUT}/app/backend/manifest.json" <<'PY'
import json, os, sys
app, base, mdir = sys.argv[1:4]
mods = sorted(f for f in os.listdir(mdir) if f.endswith(".jar"))
print(json.dumps({"appVersion": app, "requiresBase": base, "modules": mods},
                 indent=2, ensure_ascii=False))
PY

MOD_COUNT="$(find "${OUT}/app/backend/modules" -name '*.jar' | wc -l | tr -d ' ')"
echo ""
echo "==> 打包完成: ${OUT}/app  (modules=${MOD_COUNT}, requiresBase=${REQUIRES_BASE})"
