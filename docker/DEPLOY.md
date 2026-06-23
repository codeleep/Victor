# Victor AI 智能面试平台 - 本地部署包

## 环境要求
- 已安装 Docker 及 Docker Compose (Docker Desktop 自带)
- 内存 >= 2GB 空闲, 磁盘空间 >= 2GB

## 快速开始
1. 解压本压缩包到任意目录
2. 编辑 `.env` 文件, 修改以下两项为强随机值:
   - `DB_PASSWORD`  数据库密码
   - `JWT_SECRET`   JWT 签名密钥 (至少 256 位)
3. 一键启动:
   - Windows: 双击 `deploy.bat`
   - macOS / Linux: 终端执行 `sh deploy.sh`
4. 启动完成后浏览器访问 `http://localhost:80` (端口可在 `.env` 的 `APP_PORT` 修改)
5. 默认管理员账号: `admin` / `admin123` (登录后请立即修改密码)

## 常用命令
- 查看运行状态: `docker compose ps`
- 查看实时日志: `docker compose logs -f`
- 停止服务:     `docker compose down`
- 停止并清除所有数据(慎用): `docker compose down -v`

## 说明
- 首次启动数据库会自动建表并写入初始数据, 约需 30-60 秒, 期间健康检查可能显示 unhealthy, 属正常现象。
- 数据持久化在 Docker 卷 `victor-pgdata` 与 `victor-uploads` 中, 删除容器不会丢数据; 只有执行 `docker compose down -v` 才会清除。
- 如需更换端口, 修改 `.env` 中的 `APP_PORT` 后重新执行 `docker compose up -d`。
- 升级到新版本: 下载新的分发包, 解压后覆盖目录, 再次运行 deploy 脚本即可 (会加载新镜像并重启, 旧数据保留)。