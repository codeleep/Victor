# Victor AI - 智能面试模拟平台

<p align=center>
  <b>🤖 基于 Multi-Agent 的智能面试模拟平台</b>
</p>

<p align=center>
  <img src=docs/screenshots/interview-room.png width=600 alt=面试房间/>
</p>

## 功能特性

- 🤖 **Multi-Agent 面试引擎** — Agent Team 协同，面试官 / 评估者角色分工
- 🎙️ **实时语音交互** — WebSocket 流式 ASR 语音识别 + TTS 语音合成
- 📝 **多模态协作** — Markdown 编辑、Monaco 代码编辑、Excalidraw 白板绘图
- 📄 **简历解析** — 上传简历自动解析与结构化处理
- 🔍 **向量检索** — pgvector 语义检索，面试中动态召回相关题目和经验
- 📊 **智能评估** — 自动生成多维度面试报告，支持 PDF / Markdown 导出
- ⚙️ **灵活配置** — 自定义 Agent LLM、语音服务、API Key、元数据

## 功能截图

<table>
  <tr>
    <td align=center><b>登录页</b></td>
    <td align=center><b>仪表盘</b></td>
  </tr>
  <tr>
    <td><img src=docs/screenshots/login.png width=400 alt=登录页/></td>
    <td><img src=docs/screenshots/dashboard.png width=400 alt=仪表盘/></td>
  </tr>
  <tr>
    <td align=center><b>面试配置</b></td>
    <td align=center><b>面试房间</b></td>
  </tr>
  <tr>
    <td><img src=docs/screenshots/interview-config.png width=400 alt=面试配置/></td>
    <td><img src=docs/screenshots/interview-room.png width=400 alt=面试房间/></td>
  </tr>
  <tr>
    <td align=center><b>面试报告</b></td>
    <td align=center><b>系统设置</b></td>
  </tr>
  <tr>
    <td><img src=docs/screenshots/report.png width=400 alt=面试报告/></td>
    <td><img src=docs/screenshots/settings.png width=400 alt=系统设置/></td>
  </tr>
</table>

## 项目结构

```
Victor/
├── frontend/                  # 前端 (React + TypeScript)
│   ├── src/
│   │   ├── views/
│   │   │   ├── auth/          # 登录 / 注册
│   │   │   ├── dashboard/     # 仪表盘
│   │   │   ├── interview/     # 面试配置 / 面试房间 / 面试记录
│   │   │   ├── report/        # 面试报告
│   │   │   ├── resource/      # 简历 / 经验 / 题目 / 岗位
│   │   │   └── settings/      # AI / 语音 / API Key 配置
│   │   ├── components/        # 公共组件
│   │   ├── layouts/           # 布局
│   │   ├── stores/            # Zustand 状态管理
│   │   ├── utils/             # 工具 (request, websocket, audio)
│   │   └── types/             # TypeScript 类型
│   └── ...
│
├── backend/                   # 后端 (Spring Boot + Spring AI)
│   ├── victor-common/         # 通用模块 — 常量、枚举、异常、工具
│   ├── victor-infra/          # 基础设施 — Agent 框架、语音客户端
│   ├── victor-core/           # 核心业务 — 面试引擎、报告服务
│   └── victor-web/            # Web 层 — REST API、WebSocket
│
├── docker/                    # Docker 部署文件
│   ├── Dockerfile             # 多阶段构建 (前端构建 → 后端构建 → 运行时)
│   ├── nginx.conf             # Nginx 配置 (静态文件 + 反向代理)
│   └── entrypoint.sh          # 启动脚本 (Java + Nginx)
│
├── docs/                      # 文档
│   └── screenshots/           # 功能截图
│
└── docker-compose.yml         # 一键部署 (app + postgres)
```

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | React 18 · TypeScript 5 · Vite 5 · Ant Design 5 · Zustand · Monaco Editor · Excalidraw |
| 后端 | Java 21 · Spring Boot 3.3 · Spring AI · MyBatis-Plus · PostgreSQL (pgvector) · Redis · WebSocket |
| 部署 | Docker · Nginx · Docker Compose |

## 快速开始

### 本地开发

**前置条件：** Node.js 18+、JDK 21+、Maven 3.8+、PostgreSQL (pgvector)、Redis

`ash
# 1. 启动 PostgreSQL 和 Redis
docker compose up postgres -d   # 或使用本地安装

# 2. 启动后端
cd backend
mvn clean package -DskipTests
java -jar victor-web/target/victor-web-1.0-SNAPSHOT.jar

# 3. 启动前端
cd frontend
npm install
npm run dev
`

### Docker 一键部署

**前置条件：** Docker + Docker Compose

`ash
docker compose up -d
`

访问 `http://localhost` 即可使用。

首次启动后调用初始化接口：

`ash
curl -X POST http://localhost/api/v1/system/init
`

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_NAME` | 数据库名 | `victor` |
| `DB_PASSWORD` | 数据库密码 | `postgres` |
| `JWT_SECRET` | JWT 签名密钥 | (内置默认) |
| `JWT_EXPIRATION` | Token 有效期(ms) | `86400000` |

## API 概览

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `/api/v1/auth/**` | 登录注册 |
| 用户 | `/api/v1/users/**` | 用户管理 |
| 岗位 | `/api/v1/jobs/**` | 岗位 CRUD |
| 题目 | `/api/v1/questions/**` | 面试题管理 |
| 简历 | `/api/v1/resumes/**` | 简历上传解析 |
| 经验 | `/api/v1/experiences/**` | 项目经验 |
| Agent | `/api/v1/agents/**` | Agent 配置 |
| 面试 | `/api/v1/interview-configs/**` | 面试配置 |
| 会话 | `/api/v1/interview-sessions/**` | 面试流程 |
| 报告 | `/api/v1/reports/**` | 报告与导出 |
| 语音 | `/api/v1/voice/**` | ASR/TTS 配置 |
| WebSocket | `/ws` | 实时面试通信 |

## Multi-Agent 架构

面试引擎基于自研 Agent 框架（`victor-infra/agent`）：

- **Agent** — 独立角色 + LLM 配置的智能体
- **Agent Team** — 多 Agent 协同，支持 Handoff 交接
- **Tool** — Agent 可调用工具（资源查询等）
- **Guardrail** — 输入 / 输出安全护栏
- **Tracing** — 执行链路追踪

## License

MIT
