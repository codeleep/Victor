# Victor AI 面试助手 - API接口设计

## 1. 接口规范

### 1.1 通用规范
- **协议**：HTTPS
- **格式**：JSON
- **编码**：UTF-8
- **认证**：Bearer Token（JWT）
- **版本**：/api/v1/

### 1.2 统一响应格式说明

所有API响应都遵循统一的格式，包含以下三个字段：

**成功响应结构：**
- code：状态码，200表示成功
- message：响应消息，成功时为"success"
- data：响应数据，具体业务数据对象

**错误响应结构：**
- code：错误状态码（4xx或5xx）
- message：错误描述信息
- data：null或详细错误信息

### 1.3 分页响应格式说明

列表查询接口返回的分页数据结构包含：

**分页数据结构：**
- content：当前页的数据列表
- totalElements：总记录数
- totalPages：总页数
- page：当前页码（从0开始）
- size：每页大小

### 1.4 状态码定义

| 状态码 | 说明 |
|-------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

---

## 2. 用户模块 API

### 2.1 用户注册

**接口路径：** POST /api/v1/auth/register

**请求参数：**
- username：用户名（字符串，必填）
- email：邮箱地址（字符串，必填）
- password：密码（字符串，必填）

**响应数据：**
返回新创建的用户信息和访问令牌：
- id：用户ID
- username：用户名
- email：邮箱
- token：JWT访问令牌

### 2.2 用户登录

**接口路径：** POST /api/v1/auth/login

**请求参数：**
- username：用户名或邮箱（字符串，必填）
- password：密码（字符串，必填）

**响应数据：**
返回用户信息和登录凭证：
- id：用户ID
- username：用户名
- token：JWT访问令牌
- expiresIn：令牌有效期（秒），默认7天（604800秒）

### 2.3 获取用户信息

**接口路径：** GET /api/v1/users/me

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回当前用户的详细信息：
- id：用户ID
- username：用户名
- email：邮箱
- nickname：昵称
- avatar：头像URL
- status：用户状态（ACTIVE/LOCKED/DELETED）
- createdAt：创建时间

### 2.4 修改用户信息

**接口路径：** PUT /api/v1/users/me

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- nickname：昵称（字符串，可选）
- avatar：头像URL（字符串，可选）

### 2.5 修改密码

**接口路径：** PUT /api/v1/users/me/password

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- oldPassword：旧密码（字符串，必填）
- newPassword：新密码（字符串，必填）

---

## 3. 资料模块 API

### 3.1 题库管理

#### 创建题目

**接口路径：** POST /api/v1/questions

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- title：题目标题（字符串，必填）
- description：题目描述（字符串，可选）
- type：题目类型（TECHNICAL技术题/BEHAVIORAL行为题，必填）
- tags：标签列表（字符串数组，可选）
- difficulty：难度等级（EASY/MEDIUM/HARD，必填）
- referenceAnswer：参考答案（字符串，可选）
- sourceDocumentId：来源文档ID（数字，可选）

#### 查询题目列表

**接口路径：** GET /api/v1/questions

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- page：页码（数字，默认0）
- size：每页大小（数字，默认10）
- type：题目类型筛选（可选）
- difficulty：难度筛选（可选）
- tags：标签筛选（逗号分隔的字符串，可选）
- keyword：关键词搜索（可选）

**响应数据：**
返回分页的题目列表，每个题目包含：
- id：题目ID
- title：标题
- description：描述
- type：类型
- tags：标签列表
- difficulty：难度
- source：来源
- createdAt：创建时间
- updatedAt：更新时间

#### 获取题目详情

**接口路径：** GET /api/v1/questions/{id}

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回题目的完整信息，包括参考答案。

#### 更新题目

**接口路径：** PUT /api/v1/questions/{id}

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
同创建题目，所有字段可选。

#### 删除题目

**接口路径：** DELETE /api/v1/questions/{id}

**请求头：**
- Authorization: Bearer {token}

#### 审核题目

**批准题目：**
- 接口路径：POST /api/v1/questions/{id}/approve
- 功能：将待审核题目的状态改为ACTIVE

**拒绝题目：**
- 接口路径：POST /api/v1/questions/{id}/reject
- 功能：将待审核题目的状态改为REJECTED

### 3.2 岗位管理

#### 创建岗位

**接口路径：** POST /api/v1/jobs

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：岗位名称（字符串，必填）
- description：岗位描述/JD原文（字符串，必填）
- requiredSkills：所需技能列表（字符串数组，可选）
- experienceYears：工作年限要求（数字，可选）
- education：学历要求（字符串，可选）
- salaryRange：薪资范围（字符串，可选）
- domains：业务领域标签（字符串数组，可选）

#### 查询岗位列表

**接口路径：** GET /api/v1/jobs

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- page：页码（数字，默认0）
- size：每页大小（数字，默认10）
- keyword：关键词搜索（可选）
- domains：领域筛选（逗号分隔，可选）

**响应数据：**
返回分页的岗位列表。

#### 获取岗位详情

**接口路径：** GET /api/v1/jobs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新岗位

**接口路径：** PUT /api/v1/jobs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除岗位

**接口路径：** DELETE /api/v1/jobs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 审核岗位

**批准岗位：** POST /api/v1/jobs/{id}/approve

**拒绝岗位：** POST /api/v1/jobs/{id}/reject

### 3.3 简历管理

#### 上传简历

**接口路径：** POST /api/v1/resumes/upload

**请求头：**
- Authorization: Bearer {token}
- Content-Type: multipart/form-data

**请求参数：**
- file：简历文件（文件类型，支持PDF/DOC/DOCX/TXT，必填）
- name：简历名称（字符串，可选，默认为文件名）

**响应数据：**
返回创建的简历记录：
- id：简历ID
- name：简历名称
- fileName：文件名
- filePath：文件存储路径
- status：处理状态（PENDING/PARSED/EMBEDDED）
- createdAt：创建时间

#### 解析简历

**接口路径：** POST /api/v1/resumes/{id}/parse

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
调用AI服务解析简历内容，提取结构化信息。解析完成后，简历的parsed_content字段会被填充。

#### 触发向量化

**接口路径：** POST /api/v1/resumes/{id}/embed

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
将简历内容转换为向量并存储到向量数据库，用于后续的相似度召回。向量化完成后，简历的status会变为EMBEDDED，embedded_at字段会被设置。

#### 获取简历列表

**接口路径：** GET /api/v1/resumes

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- page：页码
- size：每页大小
- status：处理状态筛选（可选）

#### 获取简历详情

**接口路径：** GET /api/v1/resumes/{id}

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回简历的完整信息，包括解析后的结构化内容和摘要。

#### 更新简历

**接口路径：** PUT /api/v1/resumes/{id}

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：简历名称（可选）
- parsedContent：解析内容（可选，允许手动修正）
- summary：摘要信息（可选）

#### 删除简历

**接口路径：** DELETE /api/v1/resumes/{id}

**请求头：**
- Authorization: Bearer {token}

#### 审核简历

**批准简历：** POST /api/v1/resumes/{id}/approve

**拒绝简历：** POST /api/v1/resumes/{id}/reject

### 3.4 经历管理

#### 创建经历

**接口路径：** POST /api/v1/experiences

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- type：经历类型（PROJECT项目/WORK工作/EDUCATION教育/OTHER其他，必填）
- title：标题（字符串，必填）
- startDate：开始日期（日期格式YYYY-MM-DD，必填）
- endDate：结束日期（日期格式YYYY-MM-DD，可选）
- description：描述（字符串，可选）
- skills：相关技能列表（字符串数组，可选）
- attachments：附件URL列表（字符串数组，可选）

#### 查询经历列表

**接口路径：** GET /api/v1/experiences

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- userId：用户ID（数字，必填）
- type：经历类型筛选（可选）

**响应数据：**
返回经历列表，按创建时间倒序排列。

#### 获取经历详情

**接口路径：** GET /api/v1/experiences/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新经历

**接口路径：** PUT /api/v1/experiences/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除经历

**接口路径：** DELETE /api/v1/experiences/{id}

**请求头：**
- Authorization: Bearer {token}

#### 审核经历

**批准经历：** POST /api/v1/experiences/{id}/approve

**拒绝经历：** POST /api/v1/experiences/{id}/reject

---

## 4. Agent模块 API

### 4.1 Agent管理

#### 创建Agent

**接口路径：** POST /api/v1/agents

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：Agent名称（字符串，必填）
- role：角色（如"技术面试官"、"HR面试官"，字符串，必填）
- description：描述（字符串，可选）
- avatar：头像URL（字符串，可选）
- modes：支持的面试模式列表（字符串数组，可选）
- domains：专业领域标签（字符串数组，可选）
- modelType：使用的模型类型（字符串，必填）
- systemPrompt：系统提示词模板（字符串，必填）
- openingWords：开场白（字符串，可选）
- followUpStrategy：追问策略（AGGRESSIVE积极/MODERATE适中/CONSERVATIVE保守，可选）
- allowedTools：允许使用的工具列表（字符串数组，可选）

#### 获取Agent列表

**接口路径：** GET /api/v1/agents

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- enabled：是否只返回启用的Agent（布尔值，可选）
- role：角色筛选（可选）

**响应数据：**
返回Agent列表，每个Agent包含基本信息和配置概览。

#### 获取Agent详情

**接口路径：** GET /api/v1/agents/{id}

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回Agent的完整配置信息，包括提示词和工具配置。

#### 更新Agent

**接口路径：** PUT /api/v1/agents/{id}

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
所有字段可选，只更新提供的字段。

#### 删除Agent

**接口路径：** DELETE /api/v1/agents/{id}

**请求头：**
- Authorization: Bearer {token}

#### 启用/禁用Agent

**启用Agent：** POST /api/v1/agents/{id}/enable

**禁用Agent：** POST /api/v1/agents/{id}/disable

### 4.2 Agent团队管理

#### 创建Agent团队

**接口路径：** POST /api/v1/agent-teams

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：团队名称（字符串，必填）
- description：团队描述（字符串，可选）
- members：团队成员配置（对象数组，必填）
  - agentId：Agent ID（数字，必填）
  - role：在团队中的角色定位（字符串，必填）
  - order：执行顺序（数字，必填）
- executionMode：执行模式（SEQUENTIAL顺序/PARALLEL并行/VOTING投票，必填）

#### 获取团队列表

**接口路径：** GET /api/v1/agent-teams

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回团队列表及简要成员信息。

#### 获取团队详情

**接口路径：** GET /api/v1/agent-teams/{id}

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回团队的完整配置，包括所有成员的详细信息和执行流程。

#### 更新团队

**接口路径：** PUT /api/v1/agent-teams/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除团队

**接口路径：** DELETE /api/v1/agent-teams/{id}

**请求头：**
- Authorization: Bearer {token}

### 4.3 Agent大模型配置

#### 配置Agent模型

**接口路径：** POST /api/v1/agents/{id}/llm-config

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- modelType：模型类型（字符串，必填）
- provider：提供商（字符串，必填）
- protocol：API协议（OPENAI/ANTHROPIC/OLLAMA等，必填）
- temperature：温度参数（0-2之间的数字，可选，默认1.0）
- maxTokens：最大token数（数字，可选，默认2000）
- topP：top_p参数（0-1之间的数字，可选，默认1.0）
- apiKeyId：API密钥ID（数字，必填）

#### 获取模型配置

**接口路径：** GET /api/v1/agents/{id}/llm-config

**请求头：**
- Authorization: Bearer {token}

### 4.4 面试配置

#### 创建面试配置

**接口路径：** POST /api/v1/interview-configs

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- jobId：岗位ID（数字，可选）
- teamId：Agent团队ID（数字，必填）
- rounds：面试轮数（数字，必填，建议1-5轮）
- durationPerRound：每轮时长（分钟，数字，可选，默认10分钟）
- questionsPerRound：每轮问题数（数字，可选，默认3个问题）
- difficulty：难度等级（EASY/MEDIUM/HARD，必填）
- scoringDimensions：评分维度配置（对象，可选）
  - dimensions：维度列表（如"技术能力"、"沟通能力"、"问题解决能力"）
  - weights：各维度权重（总和应为100）
- passingScore：及格分数线（0-100的数字，可选，默认60）
- generateReport：是否生成详细报告（布尔值，可选，默认true）

#### 获取面试配置列表

**接口路径：** GET /api/v1/interview-configs

**请求头：**
- Authorization: Bearer {token}

#### 获取面试配置详情

**接口路径：** GET /api/v1/interview-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新面试配置

**接口路径：** PUT /api/v1/interview-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除面试配置

**接口路径：** DELETE /api/v1/interview-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 发布/归档配置

**发布配置：** POST /api/v1/interview-configs/{id}/publish
- 功能：将配置状态改为已发布，可以开始面试

**归档配置：** POST /api/v1/interview-configs/{id}/archive
- 功能：将配置状态改为已归档，不再使用

---

## 5. 面试会话 API

### 5.1 会话管理

#### 创建面试会话

**接口路径：** POST /api/v1/interview-sessions

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- configId：面试配置ID（数字，必填）
- resumeId：简历ID（数字，可选，用于个性化面试）

**响应数据：**
返回创建的会话信息：
- id：会话ID
- status：会话状态（WAITING等待开始）
- totalRounds：总轮数
- createdAt：创建时间

#### 开始面试

**接口路径：** POST /api/v1/interview-sessions/{id}/start

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
初始化Agent团队，设置会话状态为IN_PROGRESS，开始第一轮面试。

**响应数据：**
返回第一轮的问题：
- roundNumber：当前轮次（1）
- agentName：负责的Agent名称
- question：第一个问题文本

#### 获取会话列表

**接口路径：** GET /api/v1/interview-sessions

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- page：页码
- size：每页大小
- status：状态筛选（IN_PROGRESS/COMPLETED/CANCELLED，可选）

**响应数据：**
返回分页的会话列表，包含会话摘要信息。

#### 获取会话详情

**接口路径：** GET /api/v1/interview-sessions/{id}

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回会话的完整信息，包括当前进度和历史轮次摘要。

### 5.2 面试进行

#### 提交回答

**接口路径：** POST /api/v1/interview-sessions/{id}/answer

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- answer：用户回答内容（字符串，必填）
- duration：回答耗时（秒，数字，可选）

**响应数据：**
根据情况返回不同结果：

**如果需要追问：**
- needFollowUp：true
- followUpQuestion：追问问题
- roundNumber：当前轮次

**如果进入下一轮：**
- needFollowUp：false
- nextRound：true
- roundNumber：新轮次号
- agentName：新Agent名称
- question：新问题文本

**如果面试结束：**
- needFollowUp：false
- nextRound：false
- sessionCompleted：true
- finalScore：最终得分
- passed：是否通过

#### 跳过当前问题

**接口路径：** POST /api/v1/interview-sessions/{id}/skip

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
用户选择跳过当前问题，直接进入下一个问题或下一轮。

#### 暂停面试

**接口路径：** POST /api/v1/interview-sessions/{id}/pause

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
暂停当前面试会话，记录暂停时间。

#### 恢复面试

**接口路径：** POST /api/v1/interview-sessions/{id}/resume

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
从中断处恢复面试。

#### 取消面试

**接口路径：** POST /api/v1/interview-sessions/{id}/cancel

**请求头：**
- Authorization: Bearer {token}

**功能说明：**
取消当前面试会话，设置状态为CANCELLED。

### 5.3 面试结果

#### 获取面试报告

**接口路径：** GET /api/v1/interview-sessions/{id}/report

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回完整的面试报告：
- sessionId：会话ID
- totalScore：总分（0-100）
- passed：是否通过
- recommendationLevel：推荐等级（STRONG_RECOMMEND强烈推荐/RECOMMEND推荐/WEAK_RECOMMEND勉强推荐/NOT_RECOMMEND不推荐）
- dimensionScores：各维度得分详情
  - technicalAbility：技术能力得分
  - communicationAbility：沟通能力得分
  - problemSolvingAbility：问题解决能力得分
- strengths：优势总结（文本）
- weaknesses：劣势分析（文本）
- suggestions：改进建议（文本）
- turns：各轮次详情列表
  - roundNumber：轮次号
  - agentName：Agent名称
  - question：问题
  - answer：回答
  - score：本轮评分
  - comment：评语

#### 获取会话历史记录

**接口路径：** GET /api/v1/interview-sessions/{id}/history

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回完整的对话历史，包括所有轮次的问答记录和追问记录。

---

## 6. 语音模块 API

### 6.1 ASR配置管理

#### 创建ASR配置

**接口路径：** POST /api/v1/voice/asr-configs

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：配置名称（字符串，必填）
- provider：服务提供商（ALIYUN/TENCENT/IFTEK/BAIDU，必填）
- region：区域（字符串，可选）
- apiKey：API密钥（字符串，必填）
- apiSecret：API Secret（字符串，必填）
- endpoint：端点URL（字符串，可选）
- language：语言模型（zh-CN/BUILD-IN等，可选，默认zh-CN）
- sampleRate：采样率（数字，可选，默认16000）
- audioFormat：音频格式（wav/mp3/aac，可选，默认wav）
- isDefault：是否设为默认配置（布尔值，可选）

#### 获取ASR配置列表

**接口路径：** GET /api/v1/voice/asr-configs

**请求头：**
- Authorization: Bearer {token}

#### 获取ASR配置详情

**接口路径：** GET /api/v1/voice/asr-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新ASR配置

**接口路径：** PUT /api/v1/voice/asr-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除ASR配置

**接口路径：** DELETE /api/v1/voice/asr-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 设为默认配置

**接口路径：** POST /api/v1/voice/asr-configs/{id}/set-default

**请求头：**
- Authorization: Bearer {token}

### 6.2 TTS配置管理

#### 创建TTS配置

**接口路径：** POST /api/v1/voice/tts-configs

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：配置名称（字符串，必填）
- provider：服务提供商（ALIYUN/TENCENT/IFTEK/BAIDU，必填）
- region：区域（字符串，可选）
- apiKey：API密钥（字符串，必填）
- apiSecret：API Secret（字符串，必填）
- endpoint：端点URL（字符串，可选）
- voice：音色（字符串，必填，如"xiaoyun"、"xiaogang"）
- speed：语速（0.5-2.0之间的数字，可选，默认1.0）
- pitch：语调（0.5-2.0之间的数字，可选，默认1.0）
- volume：音量（0-100之间的数字，可选，默认50）
- isDefault：是否设为默认配置（布尔值，可选）

#### 获取TTS配置列表

**接口路径：** GET /api/v1/voice/tts-configs

**请求头：**
- Authorization: Bearer {token}

#### 获取TTS配置详情

**接口路径：** GET /api/v1/voice/tts-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新TTS配置

**接口路径：** PUT /api/v1/voice/tts-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除TTS配置

**接口路径：** DELETE /api/v1/voice/tts-configs/{id}

**请求头：**
- Authorization: Bearer {token}

#### 设为默认配置

**接口路径：** POST /api/v1/voice/tts-configs/{id}/set-default

**请求头：**
- Authorization: Bearer {token}

### 6.3 语音服务

#### 语音转文字（ASR）

**接口路径：** POST /api/v1/voice/asr

**请求头：**
- Authorization: Bearer {token}
- Content-Type: multipart/form-data

**请求参数：**
- audio：音频文件（文件类型，必填）
- configId：ASR配置ID（数字，可选，不传则使用默认配置）

**响应数据：**
返回识别结果：
- text：识别出的文本内容
- confidence：置信度（0-1之间的数字）
- duration：音频时长（秒）

#### 文字转语音（TTS）

**接口路径：** POST /api/v1/voice/tts

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- text：要转换的文本内容（字符串，必填）
- configId：TTS配置ID（数字，可选，不传则使用默认配置）
- voice：音色覆盖（字符串，可选，优先级高于配置）
- speed：语速覆盖（数字，可选）

**响应数据：**
返回生成的音频：
- audioUrl：音频文件URL
- duration：音频时长（秒）
- format：音频格式（mp3/wav）

---

## 7. 开放API模块

### 7.1 API密钥管理

#### 创建API密钥

**接口路径：** POST /api/v1/open-api/keys

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- name：密钥名称（字符串，必填）
- ipWhitelist：IP白名单列表（字符串数组，可选，为空则不限制）
- rateLimit：速率限制（次/分钟，数字，可选，默认60）
- expiresAt：过期时间（ISO 8601格式，可选，不传则永不过期）

**响应数据：**
返回创建的密钥信息：
- id：密钥ID
- name：密钥名称
- apiKey：API密钥（明文，仅在创建时返回一次）
- status：状态（ACTIVE）
- createdAt：创建时间

**重要提示：** API密钥只在创建时返回一次，请妥善保存。

#### 获取密钥列表

**接口路径：** GET /api/v1/open-api/keys

**请求头：**
- Authorization: Bearer {token}

**响应数据：**
返回密钥列表（不包含密钥明文）：
- id：密钥ID
- name：密钥名称
- ipWhitelist：IP白名单
- rateLimit：速率限制
- totalRequests：总请求次数
- todayRequests：今日请求次数
- lastUsedAt：最后使用时间
- status：状态
- expiresAt：过期时间

#### 获取密钥详情

**接口路径：** GET /api/v1/open-api/keys/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新密钥配置

**接口路径：** PUT /api/v1/open-api/keys/{id}

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
所有字段可选。

#### 禁用密钥

**接口路径：** POST /api/v1/open-api/keys/{id}/disable

**请求头：**
- Authorization: Bearer {token}

#### 启用密钥

**接口路径：** POST /api/v1/open-api/keys/{id}/enable

**请求头：**
- Authorization: Bearer {token}

#### 删除密钥

**接口路径：** DELETE /api/v1/open-api/keys/{id}

**请求头：**
- Authorization: Bearer {token}

### 7.2 数据导入接口

**认证方式：** 使用API Key进行认证，在请求头中传递：
- X-API-Key: {your_api_key}

#### 导入题目

**接口路径：** POST /api/v1/open-api/questions

**请求头：**
- X-API-Key: {api_key}
- Content-Type: application/json

**请求参数：**
- title：题目标题（字符串，必填）
- description：题目描述（可选）
- type：题目类型（TECHNICAL/BEHAVIORAL，必填）
- tags：标签列表（可选）
- difficulty：难度等级（EASY/MEDIUM/HARD，必填）
- referenceAnswer：参考答案（可选）
- externalId：外部系统ID（可选，用于幂等性控制）

**响应数据：**
返回导入结果：
- id：创建的资料ID
- ingestStatus：导入状态（PENDING_REVIEW待审核）
- message：提示信息

#### 导入岗位

**接口路径：** POST /api/v1/open-api/jobs

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- name：岗位名称（必填）
- description：岗位描述（必填）
- requiredSkills：所需技能列表（可选）
- experienceYears：工作年限要求（可选）
- education：学历要求（可选）
- salaryRange：薪资范围（可选）
- domains：领域标签（可选）
- externalId：外部系统ID（可选）

#### 导入简历

**接口路径：** POST /api/v1/open-api/resumes

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- name：简历名称（必填）
- rawText：简历文本内容（必填）
- parsedContent：解析后的结构化内容（可选，如果不提供系统会自动解析）
- externalId：外部系统ID（可选）

#### 导入经历

**接口路径：** POST /api/v1/open-api/experiences

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- type：经历类型（PROJECT/WORK/EDUCATION/OTHER，必填）
- title：标题（必填）
- startDate：开始日期（YYYY-MM-DD格式，必填）
- endDate：结束日期（YYYY-MM-DD格式，可选）
- description：描述（可选）
- skills：技能列表（可选）
- externalId：外部系统ID（可选）

#### 批量导入

**接口路径：** POST /api/v1/open-api/batch-import

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- questions：题目列表（可选）
- jobs：岗位列表（可选）
- resumes：简历列表（可选）
- experiences：经历列表（可选）

**响应数据：**
返回批量导入结果：
- successCount：成功导入数量
- failureCount：失败数量
- results：每条记录的导入结果详情

### 7.3 数据审核接口

#### 查询待审核列表

**接口路径：** GET /api/v1/open-api/pending-reviews

**请求头：**
- X-API-Key: {api_key}

**查询参数：**
- type：资料类型筛选（question/job/resume/experience，可选）
- page：页码
- size：每页大小

**响应数据：**
返回待审核的资料列表。

#### 批准资料

**接口路径：** POST /api/v1/open-api/reviews/{id}/approve

**请求头：**
- X-API-Key: {api_key}

**功能说明：**
将资料的ingest_status从PENDING_REVIEW改为ACTIVE，使其可用于召回和面试。

#### 拒绝资料

**接口路径：** POST /api/v1/open-api/reviews/{id}/reject

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- reason：拒绝原因（可选）

**功能说明：**
将资料的ingest_status改为REJECTED。

#### 批量审核

**接口路径：** POST /api/v1/open-api/batch-review

**请求头：**
- X-API-Key: {api_key}

**请求参数：**
- ids：资料ID列表（数字数组，必填）
- action：操作（APPROVE批准/REJECT拒绝，必填）
- reason：拒绝原因（当action=REJECT时可选）

---

## 8. 元数据模块 API

### 8.1 元数据管理

#### 创建元数据

**接口路径：** POST /api/v1/metadata

**请求头：**
- Authorization: Bearer {token}

**请求参数：**
- type：元数据类型（QUESTION_CATEGORY/SKILL_TAG/INDUSTRY等，必填）
- key：键名（字符串，必填，同一类型下唯一）
- displayName：显示名称（字符串，必填）
- parentId：父级ID（数字，可选，用于树形结构）
- sortOrder：排序号（数字，可选，默认0）
- description：描述（字符串，可选）
- icon：图标（字符串，可选）
- color：颜色标签（字符串，可选）
- enabled：是否启用（布尔值，可选，默认true）

#### 获取元数据列表

**接口路径：** GET /api/v1/metadata

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- type：元数据类型筛选（必填）
- enabled：是否只返回启用的（可选，默认true）

**响应数据：**
返回指定类型的元数据列表。

#### 获取元数据树

**接口路径：** GET /api/v1/metadata/tree

**请求头：**
- Authorization: Bearer {token}

**查询参数：**
- type：元数据类型筛选（必填，仅支持有层级关系的类型）

**响应数据：**
返回树形结构的元数据：
- id：节点ID
- key：键名
- displayName：显示名称
- children：子节点列表（递归结构）

#### 获取元数据详情

**接口路径：** GET /api/v1/metadata/{id}

**请求头：**
- Authorization: Bearer {token}

#### 更新元数据

**接口路径：** PUT /api/v1/metadata/{id}

**请求头：**
- Authorization: Bearer {token}

#### 删除元数据

**接口路径：** DELETE /api/v1/metadata/{id}

**请求头：**
- Authorization: Bearer {token}

---

## 9. 错误处理

### 9.1 常见错误码

**400 Bad Request - 参数错误**
- 原因：请求参数缺失、格式不正确、校验失败
- 示例：注册时邮箱格式不正确、密码强度不够

**401 Unauthorized - 未认证**
- 原因：Token缺失、Token过期、Token无效
- 示例：未携带Token访问需要认证的接口

**403 Forbidden - 无权限**
- 原因：用户没有执行该操作的权限
- 示例：普通用户尝试访问管理员接口

**404 Not Found - 资源不存在**
- 原因：请求的资源ID不存在
- 示例：查询不存在的题目ID

**409 Conflict - 资源冲突**
- 原因：资源已存在或状态冲突
- 示例：注册时使用已被占用的用户名

**429 Too Many Requests - 请求过于频繁**
- 原因：超过速率限制
- 示例：开放API超过每分钟请求限制

**500 Internal Server Error - 服务器内部错误**
- 原因：服务器处理请求时发生异常
- 示例：数据库连接失败、第三方服务调用失败

### 9.2 错误响应示例

**参数校验错误：**
```
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "fieldErrors": [
      {
        "field": "email",
        "message": "邮箱格式不正确"
      }
    ]
  }
}
```

**业务逻辑错误：**
```
{
  "code": 409,
  "message": "用户名已存在",
  "data": null
}
```

**认证失败：**
```
{
  "code": 401,
  "message": "Token已过期",
  "data": null
}
```

---

## 10. 最佳实践

### 10.1 认证与授权

1. **Token管理**
   - 在所有需要认证的请求中携带JWT Token
   - Token存储在HTTP Only Cookie或LocalStorage中
   - Token过期前及时刷新

2. **API密钥安全**
   - 不要在客户端代码中硬编码API密钥
   - 定期轮换API密钥
   - 设置合理的IP白名单和速率限制

### 10.2 请求优化

1. **分页查询**
   - 合理设置page和size参数
   - 避免一次性加载大量数据
   - 建议使用size=10-50

2. **缓存策略**
   - 对不常变化的数据（如元数据）进行客户端缓存
   - 使用ETag或Last-Modified进行条件请求

3. **错误重试**
   - 对网络错误实现指数退避重试
   - 对429错误根据Retry-After头部等待后重试
   - 不对4xx错误进行重试

### 10.3 数据导入

1. **幂等性**
   - 使用externalId保证导入的幂等性
   - 重复导入相同externalId的数据会更新而非创建

2. **批量操作**
   - 优先使用批量导入接口减少请求次数
   - 单次批量导入建议不超过100条记录

3. **异步处理**
   - 大批量导入采用异步处理方式
   - 通过回调或轮询查询导入状态

### 10.4 面试流程

1. **会话管理**
   - 创建会话后及时开始面试
   - 长时间 inactive 的会话可能被自动取消
   - 面试过程中定期保存进度

2. **超时处理**
   - 每轮面试设置合理的超时时间
   - 超时后自动提交当前回答或跳过
   - 提示用户剩余时间

3. **中断恢复**
   - 网络中断后可以从中断处恢复
   - 使用会话ID重新连接
   - 保留已完成的轮次记录
