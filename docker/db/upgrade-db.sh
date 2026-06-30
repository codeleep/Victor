#!/usr/bin/env sh
# =============================================================================
# Victor 数据库升级脚本 (在已运行的 victor-postgres 容器上执行)
#
# 流程 (容错, 任何单条 SQL 失败只打印警告不中断):
#   1. 停止 victor-app (释放数据库连接)
#   2. 备份: 全量(结构+数据) + 数据(INSERT 形式)
#   3. dropdb --force + createdb (彻底清空)
#   4. 执行容器内新版本 schema.sql + data.sql (逐条容错)
#   5. 回灌旧数据 (逐条容错: 主键冲突/外键/类型不兼容 -> 跳过该行, 打印警告)
#   6. 重启 victor-app
#
# 备份文件保存在 ./backups/, 升级失败可手动恢复。
# =============================================================================
set -u

cd "$(dirname "$0")"
mkdir -p backups

# 读取 .env
if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a; . ./.env; set +a
fi
DB_NAME="${DB_NAME:-victor}"
DB_USERNAME="${DB_USERNAME:-postgres}"
TS=$(date +%Y%m%d-%H%M%S)
FULL="backups/backup-full-${TS}.sql"
DATA="backups/backup-data-${TS}.sql"
WARN="backups/upgrade-warnings-${TS}.log"

EXEC="docker compose exec -T victor-postgres"

echo "==> [1/6] 停止 victor-app (释放数据库连接)..."
docker compose stop victor-app >/dev/null 2>&1 || true

echo "==> [2/6] 备份数据库 -> ${FULL} / ${DATA} ..."
# 全量 (结构+数据, 安全快照)
if ! $EXEC pg_dump -U "$DB_USERNAME" -d "$DB_NAME" > "$FULL" 2>>"$WARN"; then
  echo "!! 全量备份失败, 已中止 (避免数据丢失)。见 $WARN"
  docker compose start victor-app >/dev/null 2>&1 || true
  exit 1
fi
# 数据 (INSERT 形式, 用于回灌)
if ! $EXEC pg_dump --data-only --column-inserts -U "$DB_USERNAME" -d "$DB_NAME" > "$DATA" 2>>"$WARN"; then
  echo "!! 数据备份失败, 已中止。见 $WARN"
  docker compose start victor-app >/dev/null 2>&1 || true
  exit 1
fi
echo "    全量备份: $(wc -l < "$FULL") 行; 数据备份: $(wc -l < "$DATA") 行"

echo "==> [3/6] 删除并重建数据库 ${DB_NAME} ..."
$EXEC dropdb -U "$DB_USERNAME" --if-exists --force "$DB_NAME" 2>>"$WARN" || true
$EXEC createdb -U "$DB_USERNAME" "$DB_NAME" 2>>"$WARN"

echo "==> [4/6] 执行新版本 schema.sql + data.sql (逐条容错, 失败仅警告)..."
$EXEC psql -v ON_ERROR_STOP=0 -U "$DB_USERNAME" -d "$DB_NAME" \
  -f /docker-entrypoint-initdb.d/01-schema.sql 2>>"$WARN" || true
$EXEC psql -v ON_ERROR_STOP=0 -U "$DB_USERNAME" -d "$DB_NAME" \
  -f /docker-entrypoint-initdb.d/02-data.sql 2>>"$WARN" || true

echo "==> [5/6] 回灌旧数据 (逐条容错: 冲突/外键/类型不兼容 -> 跳过)..."
$EXEC psql -v ON_ERROR_STOP=0 -U "$DB_USERNAME" -d "$DB_NAME" < "$DATA" 2>>"$WARN" || true

echo "==> [6/6] 重启 victor-app ..."
docker compose start victor-app >/dev/null 2>&1 || docker compose up -d victor-app

echo ""
echo "==> 升级完成。"
echo "    备份目录: backups/"
echo "    警告日志: $WARN (若有失败行可在此排查)"
echo "    全量快照: $FULL (紧急情况可用 psql -f 恢复)"
