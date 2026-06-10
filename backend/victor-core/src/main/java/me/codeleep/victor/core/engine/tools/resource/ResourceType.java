package me.codeleep.victor.core.engine.tools.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源类型枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceType {

    JOB("job", "岗位"),
    RESUME("resume", "简历"),
    EXPERIENCE("experience", "经历");

    private final String value;
    private final String description;

    public static ResourceType fromValue(String value) {
        for (ResourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的资源类型: " + value);
    }
}
