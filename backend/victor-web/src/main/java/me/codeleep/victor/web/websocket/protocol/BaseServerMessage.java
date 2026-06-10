package me.codeleep.victor.web.websocket.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.Map;

/**
 * 服务端消息基类，提供公共序列化逻辑。
 */
@Getter
public abstract class BaseServerMessage implements ServerMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String type;

    protected BaseServerMessage(String type) {
        this.type = type;
    }

    @Override
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(toMap());
        } catch (Exception e) {
            return fallbackJson();
        }
    }

    protected abstract Map<String, Object> toMap();

    protected String fallbackJson() {
        return "{\"type\":\"" + type + "\"}";
    }
}
