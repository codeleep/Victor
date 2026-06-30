# =============================================================================
# Victor base image.
#
# Contains the STABLE runtime: JRE 21 + nginx + all third-party dependency jars
# (/app/lib). Internal project modules and the frontend dist are NOT here; they
# are bind-mounted from the host at runtime (see docker-compose.yml).
#
# This image is only rebuilt when third-party dependencies change (detected by CI
# via docker/base/manifest.py). Its version tag equals the app version that
# triggered the rebuild, e.g. victor-base:0.4.0.
#
# Build args:
#   APP_VERSION  version stamp written into /app/base-manifest.json (default dev)
# =============================================================================
ARG APP_VERSION=dev

# ---------- Stage 1: build backend & extract third-party libs ----------
FROM maven:3.9-eclipse-temurin-21 AS backend-builder

WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/victor-common/pom.xml victor-common/pom.xml
COPY backend/victor-infra/pom.xml victor-infra/pom.xml
COPY backend/victor-core/pom.xml victor-core/pom.xml
COPY backend/victor-web/pom.xml victor-web/pom.xml

RUN --mount=type=cache,target=/root/.m2 mvn -pl victor-web -am dependency:go-offline -B 2>/dev/null || true

COPY backend/ .
# Build & install the 4 internal modules (plain jars, no repackage) into local m2,
# so the subsequent copy-dependencies step can resolve inter-module deps.
RUN --mount=type=cache,target=/root/.m2 mvn -pl victor-web -am clean install -DskipTests -B
# Extract third-party runtime deps, EXCLUDING internal modules (me.codeleep),
# into a single /build/lib dir (absolute path -> union of all reactor modules).
RUN --mount=type=cache,target=/root/.m2 mvn -pl victor-web dependency:copy-dependencies \
      -DincludeScope=runtime -DexcludeGroupIds=me.codeleep \
      -DoutputDirectory=/build/lib -B

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
ARG APP_VERSION=dev

RUN apk add --no-cache nginx

# nginx config + entrypoint
COPY docker/base/nginx.conf /etc/nginx/nginx.conf
COPY docker/base/entrypoint.sh /app/entrypoint.sh

# Third-party libs (immutable, baked into the image)
COPY --from=backend-builder /build/lib /app/lib

# Base manifest: runtime only reads baseVersion for the compatibility check.
# The full dependency manifest (used by CI diffing) lives in the repo at
# docker/base/base-manifest.json and is committed/updated separately.
RUN chmod +x /app/entrypoint.sh \
 && printf '{"baseVersion":"%s"}\n' "$APP_VERSION" > /app/base-manifest.json \
 && mkdir -p /app/backend/modules /app/backend/config /root/victor/uploads

WORKDIR /app

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
