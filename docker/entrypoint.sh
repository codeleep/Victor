#!/bin/sh
set -e

# Start Spring Boot in background
java -jar /app/victor-web.jar \
  --spring.datasource.url=jdbc:postgresql://postgres:5432/${DB_NAME:-victor} \
  --spring.datasource.password=${DB_PASSWORD:-postgres} \
  --jwt.secret=${JWT_SECRET:-victor-ai-default-jwt-secret-key-must-be-at-least-256-bits-long} \
  --jwt.expiration=${JWT_EXPIRATION:-86400000} \
  &

# Wait for Spring Boot to be ready
echo Waiting for Spring Boot to start...
i=0
while [ $i -lt 60 ]; do
  if wget -q -O /dev/null http://127.0.0.1:8080/api/v1/system/init/status 2>/dev/null; then
    echo Spring Boot is ready!
    break
  fi
  sleep 2
  i=$((i+1))
done

# Start Nginx in foreground
echo Starting Nginx...
nginx -g 'daemon off;'