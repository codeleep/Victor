# Victor 数据库镜像: pgvector + 内置建表脚本与初始数据
# 容器首次启动(数据卷为空)时, Postgres 会按文件名顺序执行:
#   01-schema.sql (建表) -> 02-data.sql (初始管理员账号)
FROM pgvector/pgvector:pg16

COPY backend/victor-web/src/main/resources/db/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY backend/victor-web/src/main/resources/db/data.sql   /docker-entrypoint-initdb.d/02-data.sql