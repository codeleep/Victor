# 牛客网题目导入 Victor 插件

Chrome Manifest V3 插件，用于从牛客网面试题库页面抓取题目，并通过 Victor API Key 上传到 Victor AI 题库。

## 功能特性

- 自动识别牛客网面试题页面
- 抓取题目和答案解析
- 自动点击题目获取完整答案
- 默认使用 `X-API-Key` 上传题目
- 支持 JWT Token 兼容模式
- 支持自定义抓取间隔
- 保留题目来源信息

## 安装

1. 打开 Chrome/Edge 浏览器，进入 `chrome://extensions/`
2. 开启“开发者模式”
3. 点击“加载已解压的扩展程序”
4. 选择 `plugins/web-nowcoder` 目录

## 使用方法

1. 启动 Victor 后端，确保 `http://localhost:8080` 可访问
2. 登录 Victor，在 API Key 管理页面创建一个启用状态的 API Key
3. 登录牛客网，打开任意面试题库页面：
   - 示例：`https://www.nowcoder.com/exam/interview/97443071/test?paperId=63849081`
4. 点击浏览器工具栏的“牛客题库导入 Victor”图标
5. 认证方式选择 `API Key (推荐)`，填写 Victor API Key
6. 点击“开始抓取并上传”

## 支持的页面类型

- 面试题库页面（`/exam/interview/*`）
- 面试经验页面（开发中）

## 配置项

- **Victor 后端地址**: Victor 后端 API 地址，默认 `http://localhost:8080`
- **认证方式**: 默认 `API Key`，也可切换到 `JWT Token`
- **Victor API Key**: 在 Victor 系统中创建的 API Key，用于 `X-API-Key` 请求头上传题目
- **Victor Token**: JWT 兼容模式使用，可从已登录 Victor 页面自动获取
- **题目抓取间隔**: 每道题之间的等待时间（毫秒），默认 1500ms
- **题目描述中包含来源信息**: 在描述中添加牛客网来源链接

## 文件结构

```
plugins/web-nowcoder/
├── manifest.json     # 扩展清单
├── popup.html        # 弹出页面 UI
├── popup.js          # 弹出页面逻辑
├── content.js        # 页面内容脚本
├── background.js     # 后台服务 Worker
└── README.md         # 本文件
```

## 注意事项

1. 题目上传建议使用 API Key，不依赖 Victor 登录态
2. 抓取间隔建议不要太短，避免触发反爬
3. 如果答案为空，请手动点击题目确认页面正常加载
4. 请遵守牛客网使用条款，合理使用本插件
