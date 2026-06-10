package me.codeleep.victor.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    UNPROCESSABLE_ENTITY(422, "无法处理的实体"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 服务端错误 5xx
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 业务错误 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USERNAME_ALREADY_EXISTS(1003, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(1004, "邮箱已存在"),
    PASSWORD_INCORRECT(1005, "密码错误"),
    USER_LOCKED(1006, "用户已被锁定"),
    LOGIN_FAILURE_LIMIT_EXCEEDED(1007, "登录失败次数过多，请稍后再试"),

    // 资料模块错误 2xxx
    RESUME_NOT_FOUND(2001, "简历不存在"),
    RESUME_PARSE_FAILED(2002, "简历解析失败"),
    RESUME_NOT_PARSED(2003, "简历未解析"),
    JOB_NOT_FOUND(2004, "岗位不存在"),
    QUESTION_NOT_FOUND(2005, "题目不存在"),
    EXPERIENCE_NOT_FOUND(2006, "经历不存在"),

    // 知识库模块错误 3xxx
    DOCUMENT_NOT_FOUND(3001, "文档不存在"),
    DOCUMENT_PARSE_FAILED(3002, "文档解析失败"),
    DOCUMENT_EMBED_FAILED(3003, "文档嵌入失败"),
    DOCUMENT_CHUNK_NOT_FOUND(3004, "文档块不存在"),

    // 面试模块错误 4xxx
    INTERVIEW_CONFIG_NOT_FOUND(4001, "面试配置不存在"),
    INTERVIEW_SESSION_NOT_FOUND(4002, "面试会话不存在"),
    INTERVIEW_TEMPLATE_NOT_FOUND(4003, "面试模板不存在"),
    INTERVIEW_ALREADY_STARTED(4004, "面试已开始"),
    INTERVIEW_NOT_STARTED(4005, "面试未开始"),
    INTERVIEW_ALREADY_COMPLETED(4006, "面试已结束"),
    INTERVIEW_STATUS_INVALID(4007, "面试状态无效"),
    RECALL_CONFIG_NOT_FOUND(4008, "召回配置不存在"),
    RECALL_ITEM_NOT_FOUND(4009, "召回数据项不存在"),

    // Agent模块错误 5xxx
    AGENT_NOT_FOUND(5001, "Agent不存在"),
    AGENT_TEAM_NOT_FOUND(5002, "Agent团队不存在"),
    MODEL_INSTANCE_NOT_FOUND(5003, "模型实例不存在"),
    LLM_CALL_FAILED(5004, "LLM调用失败"),
    LLM_TIMEOUT(5005, "LLM调用超时"),

    // 语音模块错误 6xxx
    VOICE_SERVICE_NOT_FOUND(6001, "语音服务配置不存在"),
    ASR_FAILED(6002, "语音识别失败"),
    TTS_FAILED(6003, "语音合成失败"),

    // 报告模块错误 7xxx
    REPORT_NOT_FOUND(7001, "报告不存在"),
    REPORT_GENERATE_FAILED(7002, "报告生成失败"),

    // 插件模块错误 8xxx
    PLUGIN_NOT_FOUND(8001, "插件不存在"),
    PLUGIN_INSTALL_FAILED(8002, "插件安装失败"),
    PLUGIN_RUN_FAILED(8003, "插件运行失败"),
    PLUGIN_AUTH_INVALID(8004, "插件认证无效"),

    // 通用资源模块错误 9xxx
    RESOURCE_NOT_FOUND(9001, "资源不存在"),
    FILE_UPLOAD_FAILED(9002, "文件上传失败");

    private final Integer code;
    private final String message;
}
