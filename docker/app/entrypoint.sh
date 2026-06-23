#!/bin/sh
set -e

DB_HOST="${DB_HOST:-victor-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-victor}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
JWT_SECRET="${JWT_SECRET:-victor-ai-default-jwt-secret-key-must-be-at-least-256-bits-long}"
JWT_EXPIRATION="${JWT_EXPIRATION:-86400000}"

shutdown() {
  echo "Shutting down services..."
  kill "$JAVA_PID" "$NGINX_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap shutdown INT TERM

java -jar /app/victor-web.jar \
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