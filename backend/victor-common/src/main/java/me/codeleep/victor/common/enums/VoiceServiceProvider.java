package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 语音服务提供商枚举
 */
@Getter
@AllArgsConstructor
public enum VoiceServiceProvider {

    ALIYUN("ALIYUN", "阿里云"),
    TENCENT("TENCENT", "腾讯云"),
    QWEN("QWEN", "通义千问"),
    VOLCENGINE("VOLCENGINE", "火山方舟"),
    AZURE("AZURE", "Azure"),
    OPENAI("OPENAI", "OpenAI");

    private final String value;
    private final String description;
}
