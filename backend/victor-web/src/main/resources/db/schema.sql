-- =====================================================
-- Victor AI 面试助手 - 数据库初始化脚本 (DDL)
-- 基于 03-数据库设计.md
-- =====================================================

-- 启用pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================================
-- 删除已有表（按外键依赖逆序）
-- =====================================================

DROP TABLE IF EXISTS interview_turn CASCADE;
DROP TABLE IF EXISTS interview_report CASCADE;
DROP TABLE IF EXISTS interview_question CASCADE;
DROP TABLE IF EXISTS interview_config CASCADE;
DROP TABLE IF EXISTS agent_team_member CASCADE;
DROP TABLE IF EXISTS agent_memory CASCADE;
DROP TABLE IF EXISTS agent_team CASCADE;
DROP TABLE IF EXISTS agent CASCADE;
DROP TABLE IF EXISTS agent_llm_config CASCADE;
DROP TABLE IF EXISTS res_experience CASCADE;
DROP TABLE IF EXISTS res_resume CASCADE;
DROP TABLE IF EXISTS res_job CASCADE;
DROP TABLE IF EXISTS res_question CASCADE;
DROP TABLE IF EXISTS voice_asr_config CASCADE;
DROP TABLE IF EXISTS voice_tts_config CASCADE;
DROP TABLE IF EXISTS open_api_key CASCADE;
DROP TABLE IF EXISTS user_ext_config CASCADE;
DROP TABLE IF EXISTS meta_metadata CASCADE;
DROP TABLE IF EXISTS login_failure_log CASCADE;
DROP TABLE IF EXISTS "user" CASCADE;

-- =====================================================
-- 1. 用户模块
-- =====================================================

CREATE TABLE "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_username ON "user" (username);
CREATE INDEX idx_user_email ON "user" (email);
CREATE INDEX idx_user_status ON "user" (status);

CREATE TABLE login_failure_log (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_login_failure_username ON login_failure_log (username);

-- =====================================================
-- 2. 开放接入模块（需在资料模块之前，因为资料表引用open_api_key）
-- =====================================================

CREATE TABLE open_api_key (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    api_key VARCHAR(255) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]',
    default_ingest_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, api_key)
);

COMMENT ON COLUMN open_api_key.scopes IS '开放接口权限范围，如IMPORT_JOB、IMPORT_QUESTION、IMPORT_RESUME、IMPORT_EXPERIENCE';
COMMENT ON COLUMN open_api_key.default_ingest_status IS '通过该Key导入资料后的默认采集状态，仅允许ACTIVE或PENDING_REVIEW';
COMMENT ON COLUMN open_api_key.status IS '状态: ENABLED-启用, DISABLED-禁用';

CREATE INDEX idx_open_api_key_user_id ON open_api_key (user_id);
CREATE INDEX idx_open_api_key_api_key ON open_api_key (api_key);
CREATE INDEX idx_open_api_key_status ON open_api_key (status);

-- =====================================================
-- 3. 资料模块
-- =====================================================

CREATE TABLE res_question (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,
    tags JSONB DEFAULT '[]',
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    reference_answer TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'USER',
    ingest_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    source_api_key_id BIGINT,
    source_uri VARCHAR(1000),
    external_id VARCHAR(255),
    raw_payload JSONB DEFAULT '{}',
    import_error TEXT,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_res_question_user_id ON res_question (user_id);
CREATE INDEX idx_res_question_type ON res_question (type);
CREATE INDEX idx_res_question_difficulty ON res_question (difficulty);
CREATE INDEX idx_res_question_tags ON res_question USING GIN (tags);
CREATE INDEX idx_res_question_ingest_status ON res_question (ingest_status);

CREATE TABLE res_job (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    required_skills JSONB DEFAULT '[]',
    experience_years INTEGER,
    education VARCHAR(50),
    salary_range VARCHAR(50),
    domains JSONB DEFAULT '[]',
    ingest_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    source_api_key_id BIGINT,
    source_uri VARCHAR(1000),
    external_id VARCHAR(255),
    raw_payload JSONB DEFAULT '{}',
    import_error TEXT,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_res_job_user_id ON res_job (user_id);
CREATE INDEX idx_res_job_name ON res_job (name);
CREATE INDEX idx_res_job_skills ON res_job USING GIN (required_skills);
CREATE INDEX idx_res_job_ingest_status ON res_job (ingest_status);

CREATE TABLE res_resume (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    raw_text TEXT,
    parsed_content JSONB DEFAULT '{}',
    summary JSONB DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ingest_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    source_api_key_id BIGINT,
    source_uri VARCHAR(1000),
    external_id VARCHAR(255),
    raw_payload JSONB DEFAULT '{}',
    import_error TEXT,
    embedded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_res_resume_user_id ON res_resume (user_id);
CREATE INDEX idx_res_resume_status ON res_resume (status);
CREATE INDEX idx_res_resume_ingest_status ON res_resume (ingest_status);

CREATE TABLE res_experience (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    description TEXT,
    skills JSONB DEFAULT '[]',
    attachments JSONB DEFAULT '[]',
    ingest_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    source_api_key_id BIGINT,
    source_uri VARCHAR(1000),
    external_id VARCHAR(255),
    raw_payload JSONB DEFAULT '{}',
    import_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_res_experience_user_id ON res_experience (user_id);
CREATE INDEX idx_res_experience_type ON res_experience (type);
CREATE INDEX idx_res_experience_ingest_status ON res_experience (ingest_status);

-- 资料表外键约束（引用open_api_key）
ALTER TABLE res_question ADD CONSTRAINT fk_res_question_source_api_key FOREIGN KEY (source_api_key_id) REFERENCES open_api_key(id);
ALTER TABLE res_job ADD CONSTRAINT fk_res_job_source_api_key FOREIGN KEY (source_api_key_id) REFERENCES open_api_key(id);
ALTER TABLE res_resume ADD CONSTRAINT fk_res_resume_source_api_key FOREIGN KEY (source_api_key_id) REFERENCES open_api_key(id);
ALTER TABLE res_experience ADD CONSTRAINT fk_res_experience_source_api_key FOREIGN KEY (source_api_key_id) REFERENCES open_api_key(id);

-- =====================================================
-- 4. Agent模块
-- =====================================================

CREATE TABLE agent_llm_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    provider VARCHAR(50) NOT NULL,
    api_endpoint VARCHAR(500) NOT NULL,
    auth_params JSONB NOT NULL,
    protocol VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(20) NOT NULL DEFAULT 'INFERENCE',
    temperature DECIMAL(3,2) DEFAULT 0.70,
    max_tokens INTEGER DEFAULT 4096,
    extra_params JSONB DEFAULT '{}',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_llm_config_user_id ON agent_llm_config (user_id);
CREATE INDEX idx_agent_llm_config_provider ON agent_llm_config (provider);
CREATE INDEX idx_agent_llm_config_model_type ON agent_llm_config (model_type);

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    key VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    llm_config_id BIGINT REFERENCES agent_llm_config(id),
    available_tools JSONB DEFAULT '[]',
    type VARCHAR(20) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, key)
);

CREATE INDEX idx_agent_user_id ON agent (user_id);
CREATE INDEX idx_agent_type ON agent (type);
CREATE INDEX idx_agent_user_key ON agent (user_id, key);

CREATE TABLE agent_team (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    key VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    main_agent_id BIGINT REFERENCES agent(id),
    members JSONB NOT NULL,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'PARALLEL',
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, key)
);

CREATE INDEX idx_team_user_id ON agent_team (user_id);
CREATE INDEX idx_team_user_key ON agent_team (user_id, key);

CREATE TABLE agent_team_member (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES agent_team(id) ON DELETE CASCADE,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    role VARCHAR(100),
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(team_id, agent_id)
);

CREATE INDEX idx_team_member_team_id ON agent_team_member (team_id);
CREATE INDEX idx_team_member_agent_id ON agent_team_member (agent_id);

CREATE TABLE agent_memory (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    state_key VARCHAR(255) NOT NULL,
    state_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, session_id, state_key)
);

CREATE INDEX idx_agent_memory_user_id ON agent_memory (user_id);
CREATE INDEX idx_agent_memory_user_session ON agent_memory (user_id, session_id);

-- =====================================================
-- 5. 面试模块
-- =====================================================

CREATE TABLE interview_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    job_id BIGINT REFERENCES res_job(id),
    resume_id BIGINT REFERENCES res_resume(id),
    rounds JSONB DEFAULT '[]',
    difficulty_config JSONB DEFAULT '{}',
    duration_minutes INTEGER,
    hint_enabled BOOLEAN DEFAULT FALSE,
    team_config JSONB DEFAULT '{}',
    agent_model_mapping JSONB DEFAULT '{}',
    recall_strategy VARCHAR(50) DEFAULT 'HYBRID',
    max_recall_count INTEGER DEFAULT 50,
    recall_items JSONB DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generate_error TEXT,
    current_question_id BIGINT,
    started_at TIMESTAMP,
    paused_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN interview_config.status IS '状态: DRAFT-草稿, GENERATING-题目生成中, GENERATE_FAILED-题目生成失败, READY-题目就绪, IN_PROGRESS-进行中, PAUSED-已暂停, COMPLETED-已完成, ABANDONED-已放弃, ARCHIVED-已归档';
COMMENT ON COLUMN interview_config.recall_items IS '召回列表工作区';

CREATE INDEX idx_config_user_id ON interview_config (user_id);
CREATE INDEX idx_config_status ON interview_config (status);

CREATE TABLE interview_question (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL REFERENCES interview_config(id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL,
    question_type VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    question_text TEXT NOT NULL,
    answer_hint JSONB DEFAULT '{}',
    source_recall_refs JSONB DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(config_id, order_index)
);

COMMENT ON COLUMN interview_question.answer_hint IS '期望答案结构，包含type、content、points、intent等字段';
COMMENT ON COLUMN interview_question.source_recall_refs IS '本题生成依据的召回引用快照';

CREATE INDEX idx_interview_question_config_id ON interview_question (config_id);

ALTER TABLE interview_config ADD CONSTRAINT fk_interview_config_current_question FOREIGN KEY (current_question_id) REFERENCES interview_question(id);

CREATE TABLE interview_turn (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES interview_config(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES interview_question(id),
    turn_index INTEGER NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    speaker VARCHAR(20) NOT NULL,
    is_followup BOOLEAN DEFAULT FALSE,
    content TEXT,
    reasoning TEXT,
    tool_events JSONB DEFAULT '[]',
    attachments JSONB DEFAULT '[]',
    is_hint BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(session_id, question_id, attempt_no, turn_index)
);

COMMENT ON COLUMN interview_turn.attachments IS '附件数组，支持AUDIO、IMAGE、CODE、MERMAID等类型';
COMMENT ON COLUMN interview_turn.tool_events IS 'AI 回合的结构化工具事件列表，前端渲染为任务块时间线';
COMMENT ON COLUMN interview_turn.reasoning IS 'AI 回合的推理过程文本（thinking+tool），供前端折叠展示；用户回合为空';

CREATE INDEX idx_turn_session_id ON interview_turn (session_id);
CREATE INDEX idx_turn_question_id ON interview_turn (question_id);

CREATE TABLE interview_report (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE REFERENCES interview_config(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    overall_score DECIMAL(3,1),
    dimension_scores JSONB DEFAULT '{}',
    per_question_evaluation JSONB DEFAULT '[]',
    summary TEXT,
    strengths TEXT,
    weaknesses TEXT,
    suggestions TEXT,
    evaluation_error TEXT,
    evaluation_retry_count INTEGER NOT NULL DEFAULT 0,
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN interview_report.status IS '状态: PENDING-待评估, EVALUATING-评估中, COMPLETED-评估完成, FAILED-评估失败';

CREATE INDEX idx_report_session_id ON interview_report (session_id);
CREATE INDEX idx_report_user_id ON interview_report (user_id);
CREATE INDEX idx_report_status ON interview_report (status);

-- =====================================================
-- 6. 语音模块
-- =====================================================

CREATE TABLE voice_asr_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    provider VARCHAR(50) NOT NULL,
    api_endpoint VARCHAR(500) NOT NULL,
    auth_params JSONB NOT NULL,
    language VARCHAR(20) DEFAULT 'zh-CN',
    extra_params JSONB DEFAULT '{}',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voice_asr_config_user_id ON voice_asr_config (user_id);
CREATE INDEX idx_voice_asr_config_provider ON voice_asr_config (provider);

CREATE TABLE voice_tts_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    provider VARCHAR(50) NOT NULL,
    api_endpoint VARCHAR(500) NOT NULL,
    auth_params JSONB NOT NULL,
    voice_name VARCHAR(100),
    extra_params JSONB DEFAULT '{}',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voice_tts_config_user_id ON voice_tts_config (user_id);
CREATE INDEX idx_voice_tts_config_provider ON voice_tts_config (provider);

-- =====================================================
-- 7. 元数据模块
-- =====================================================

CREATE TABLE meta_metadata (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    extra_data JSONB DEFAULT '{}',
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category, code)
);

CREATE INDEX idx_meta_metadata_category ON meta_metadata (category);
CREATE INDEX idx_meta_metadata_is_active ON meta_metadata (is_active);

-- =====================================================
-- 8. 用户扩展配置模块
-- =====================================================

CREATE TABLE user_ext_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, config_key)
);

CREATE INDEX idx_user_ext_config_user_id ON user_ext_config (user_id);