package me.codeleep.victor.web.websocket.processor;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文本处理上下文。
 *
 * <p>包含session级别信息，可用于存储对话历史、用户偏好等。</p>
 */
public class ProcessingContext {

    /** 属性 key：用户 ID */
    public static final String ATTR_USER_ID = "userId";
    /** 属性 key：Agent 唯一标识 */
    public static final String ATTR_AGENT_KEY = "agentKey";
    /** 属性 key：Agent 执行上下文 */
    public static final String ATTR_AGENT_CONTEXT = "agentContext";
    /** 属性 key：面试会话 ID（InterviewSession.id） */
    public static final String ATTR_INTERVIEW_SESSION_ID = "interviewSessionId";
    /** 属性 key：当前题目 ID（InterviewQuestion.id） */
    public static final String ATTR_CURRENT_QUESTION_ID = "currentQuestionId";
    /** 属性 key：Agent 定义（会话初始化时构建，不可变） */
    public static final String ATTR_AGENT_DEFINITION = "agentDefinition";
    /** 属性 key：本次用户原始文本（不包含绘图 JSON 提示块） */
    public static final String ATTR_INPUT_TEXT = "inputText";
    /** 属性 key：本次用户附件列表 */
    public static final String ATTR_ATTACHMENTS = "attachments";

    /** 会话 ID */
    @Getter
    private final String sessionId;
    private final Map<String, Object> attributes;
    /** 上下文创建时间 */
    @Getter
    private final Instant timestamp;

    public ProcessingContext(String sessionId) {
        this.sessionId = sessionId;
        this.attributes = new ConcurrentHashMap<>();
        this.timestamp = Instant.now();
    }

    public ProcessingContext(String sessionId, Map<String, Object> attributes) {
        this.sessionId = sessionId;
        this.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : new ConcurrentHashMap<>();
        this.timestamp = Instant.now();
    }

    /**
     * 获取所有属性。
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * 获取指定属性。
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 设置属性。
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 移除属性。
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

}
