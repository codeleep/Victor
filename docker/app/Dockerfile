# ==================== Stage 1: Build Frontend ====================
FROM node:20-alpine AS frontend-builder

# 构建时注入版本号 (由 CI 通过 --build-arg 传入), 默认 dev
ARG APP_VERSION=dev
ENV APP_VERSION=$APP_VERSION

WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ==================== Stage 2: Build Backend ====================
FROM maven:3.9-eclipse-temurin-21 AS backend-builder

WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/victor-common/pom.xml victor-common/pom.xml
COPY backend/victor-infra/pom.xml victor-infra/pom.xml
COPY backend/victor-core/pom.xml victor-core/pom.xml
COPY backend/victor-web/pom.xml victor-web/pom.xml

RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B 2>/dev/null || true

COPY backend/ .
RUN --mount=type=cache,target=/root/.m2 mvn -pl victor-web -am clean package -DskipTests -B

# ==================== Stage 3: Runtime ====================
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache nginx

# Copy frontend build output to nginx
COPY --from=frontend-builder /build/frontend/dist /usr/share/nginx/html

# Copy nginx config
COPY docker/app/nginx.conf /etc/nginx/nginx.conf

# Copy backend jar
COPY --from=backend-builder /build/backend/victor-web/target/victor-web-1.0-SNAPSHOT.jar /app/victor-web.jar

# Copy entrypoint
COPY docker/app/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Create upload directory
RUN mkdir -p /root/victor/uploads

WORKDIR /app

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]