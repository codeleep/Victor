# Victor AI 智能面试平台 - 本地部署包

## 架构说明

本部署包采用 **基础镜像 + 挂载应用** 的分离架构:

- **victor-base 镜像**: 内含 JRE 21 + nginx + 全部第三方依赖 jar (固定, 不随业务变化)。
  仅当第三方依赖发生版本/数量变化时才发新基础镜像, 版本号 = 触发变化的 app 版本。
- **app 目录 (挂载)**: 前端 dist + 后端 4 个业务模块 jar + 版本清单。
  每次发版只更新这里, 不动镜像。通过 bind mount 挂入容器。
- **victor-db 镜像**: pgvector + 建表/初始数据脚本, 每次发版随包提供。

启动时容器会校验 `app/backend/manifest.json` 中的 `requiresBase` 与镜像内的
`baseVersion` 是否一致, 不一致将拒绝启动并提示下载对应基础镜像。

## 环境要求
- 已安装 Docker 及 Docker Compose (Docker Desktop 自带)
- 内存 >= 2GB 空闲, 磁盘空间 >= 2GB

## 快速开始 (首次部署)
1. 解压本压缩包到任意目录
2. 编辑 `.env` 文件, 修改以下两项为强随机值:
   - `DB_PASSWORD`  数据库密码
   - `JWT_SECRET`   JWT 签名密钥 (至少 256 位)
3. 一键启动:
   - Windows: 双击 `deploy.bat`
   - macOS / Linux: 终端执行 `sh deploy.sh`
4. 启动完成后浏览器访问 `http://localhost:80` (端口可在 `.env` 的 `APP_PORT` 修改)
5. 默认管理员账号: `admin` / `admin123` (登录后请立即修改密码)

> 若 `deploy` 提示缺少基础镜像 `victor-base:<版本>`, 说明本次发版未带该基础镜像
> (依赖未变化, 复用旧基础镜像)。请从发布该基础镜像的那个 Release 下载对应的
> base 包, `docker load` 后重试。

## 升级到新版本
- **依赖未变化的小版本**: 下载新分发包, 用其中的 `app/` 目录覆盖旧目录
  (镜像不变), 重新运行 `deploy` 脚本即可。若数据库 schema 有变化, 额外执行
  `upgrade-db.sh` / `upgrade-db.bat`。
- **依赖变化的版本**: 新分发包内 `images/` 会包含新的 `victor-base` 镜像,
  `deploy` 脚本会自动加载。

## 数据库升级 (仅 schema 变化时执行)
执行 `upgrade-db.sh` (macOS/Linux) 或 `upgrade-db.bat` (Windows), 脚本会:
1. 停止应用并备份全量数据 (结构+数据) 到 `backups/`
2. 删除并重建数据库
3. 逐条执行新版本 schema/data (单条失败仅警告不中断)
4. 容错回灌旧数据 (主键/外键/类型冲突的行跳过并记录警告)

> 备份文件保留在 `backups/`, 紧急情况可用 `psql -f` 手动恢复。

## 常用命令
- 查看运行状态: `docker compose ps`
- 查看实时日志: `docker compose logs -f`
- 停止服务:     `docker compose down`
- 停止并清除所有数据(慎用): `docker compose down -v`

## 说明
- 首次启动数据库会自动建表并写入初始数据, 约需 30-60 秒, 期间健康检查可能显示 unhealthy, 属正常现象。
- 数据持久化在 Docker 卷 `victor-pgdata` 与 `victor-uploads` 中, 删除容器不会丢数据; 只有执行 `docker compose down -v` 才会清除。
- 如需更换端口, 修改 `.env` 中的 `APP_PORT` 后重新执行 `docker compose up -d`。
