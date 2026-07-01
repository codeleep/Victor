#!/bin/sh
set -e

# ---------------------------------------------------------------------------
# Victor runtime entrypoint (lives inside the base image).
#
# Third-party libs are baked into the image at /app/lib (immutable).
# Project modules + frontend are bind-mounted from the host:
#   /app/backend/modules/*.jar   (4 internal module jars)
#   /app/backend/manifest.json   (app version + requiresBase)
#   /usr/share/nginx/html        (frontend dist)
#
# Before starting, we verify the app's required base image version matches
# the base version baked into THIS image. A mismatch means the deploy bundle
# was paired with a different base image than the one running -> refuse to
# start with an actionable message.
# ---------------------------------------------------------------------------

DB_HOST="${DB_HOST:-victor-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-victor}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
JWT_SECRET="${JWT_SECRET:-victor-ai-default-jwt-secret-key-must-be-at-least-256-bits-long}"
JWT_EXPIRATION="${JWT_EXPIRATION:-86400000}"

BASE_MANIFEST="/app/base-manifest.json"
APP_MANIFEST="/app/backend/manifest.json"

# --- base/app version compatibility check ---
if [ -f "$APP_MANIFEST" ]; then
  APP_REQ=$(sed -n 's/.*"requiresBase"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$APP_MANIFEST" | head -n1)
  BASE_VER=$(sed -n 's/.*"baseVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$BASE_MANIFEST" | head -n1)
  APP_VER=$(sed -n 's/.*"appVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$APP_MANIFEST" | head -n1)

  if [ -z "$BASE_VER" ]; then
    echo "[FATAL] Base image has no baseVersion in $BASE_MANIFEST. Image is corrupt."
    exit 1
  fi

  if [ -n "$APP_REQ" ] && [ "$APP_REQ" != "$BASE_VER" ]; then
    echo "=============================================================="
    echo "[FATAL] Base image version mismatch."
    echo "  App v${APP_VER:-?} requires base v${APP_REQ}"
    echo "  This image provides base v${BASE_VER}"
    echo ""
    echo "  Please load the matching base image 'victor-base:${APP_REQ}'"
    echo "  (download the base bundle from the release that published"
    echo "  victor-base:${APP_REQ}) and restart. See DEPLOY.md."
    echo "=============================================================="
    exit 1
  fi
  echo "[ok] App v${APP_VER:-?} <-> base v${BASE_VER} (requiresBase v${APP_REQ:-?})"
else
  echo "[warn] No app manifest at $APP_MANIFEST; skipping version check (dev mode)."
fi

shutdown() {
  echo "Shutting down services..."
  kill "$JAVA_PID" "$NGINX_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap shutdown INT TERM

# --- launch Spring Boot via classpath (lib from image + modules from mount) ---
java \
  -cp "/app/lib/*:/app/backend/modules/*" \
  -Dfile.encoding=UTF-8 \
  me.codeleep.victor.web.VictorApplication \
  --spring.config.additional-location=file:/app/backend/config/ \
  --spring.datasource.url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  --spring.datasource.username="${DB_USERNAME}" \
  --spring.datasource.password="${DB_PASSWORD}" \
  --jwt.secret="${JWT_SECRET}" \
  --jwt.expiration="${JWT_EXPIRATION}" &
JAVA_PID=$!

echo "Starting Nginx..."
nginx -g 'daemon off;' &
NGINX_PID=$!

while true; do
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    echo "Spring Boot process stopped."
    wait "$JAVA_PID"
    exit $?
  fi

  if ! kill -0 "$NGINX_PID" 2>/dev/null; then
    echo "Nginx process stopped."
    wait "$NGINX_PID"
    exit $?
  fi

  sleep 2
done
