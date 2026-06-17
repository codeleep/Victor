# web-mianshiya

Chrome Manifest V3 插件，用于在已登录的 [面试鸭](https://www.mianshiya.com/) 页面中读取题库，并上传到 Victor AI 的题库接口。

## 调研到的面试鸭接口

参考 `yuyuanweb/mianshiya-plugin`：

- `POST https://api.mianshiya.com/api/question_bank/list_question`
  - 请求体：`{ current, pageSize, questionBankId }`
  - 返回：题库内题目分页，题目字段包括 `id`、`title`、`content`、`tagList`、`difficulty`、`bestQuestionAnswer`
- `POST https://api.mianshiya.com/api/question_answer/list/by_question`
  - 请求体：`{ current, pageSize, questionId }`
  - 返回：题目答案列表

插件在 `www.mianshiya.com` 页面内执行 `fetch(..., { credentials: 'include' })`，复用浏览器里已登录的面试鸭 Cookie。

## 安装

1. 打开 Chrome/Edge：`chrome://extensions/`
2. 开启“开发者模式”
3. 点击“加载已解压的扩展程序”
4. 选择目录：`plugins/web-mianshiya`

## 使用

1. 先登录 Victor AI，并保持 Victor 页面打开；插件会优先自动读取 `localStorage.token`，也可以手动填写。
2. 再登录面试鸭。
3. 打开目标题库页，例如：`https://www.mianshiya.com/bank/1787463103423897602`
4. 点击浏览器扩展图标 `web-mianshiya`
5. 填入：
   - `Victor API 地址`：默认 `http://localhost:8080`
   - `Victor Token`：Victor 登录 token
   - `题库 ID`：通常会从当前 URL 自动识别
6. 点击“开始抓取并上传”。

## 上传映射

上传到 Victor：`POST /api/v1/questions`

- `title` ← 面试鸭题目标题
- `description` ← 题目内容 + 来源题库 ID + 原题 ID + 原题链接
- `tags` ← 面试鸭 `tagList`
- `difficulty`：`1 -> EASY`，`3 -> MEDIUM`，`5 -> HARD`
- `type` 固定为 `TECHNICAL`
- `referenceAnswer` ← 最佳答案或答案列表第一项

## 注意

- 插件不会保存面试鸭 Cookie，只复用当前页面登录态。
- 面试鸭题库 ID 和题目 ID 都是长整型字符串，插件会按字符串提交，避免 JavaScript 数字精度丢失导致返回 0 题。
- 修改插件源码后，需要在 `chrome://extensions/` 点击 `web-mianshiya` 的重新加载按钮，并刷新面试鸭题库页。
- Victor Token 存在 Chrome 扩展本地存储中，仅用于请求你的 Victor 后端。
- 如果上传中途失败，会跳过当前题并继续下一题。
