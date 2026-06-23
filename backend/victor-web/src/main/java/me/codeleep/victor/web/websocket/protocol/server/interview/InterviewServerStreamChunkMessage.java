package me.codeleep.victor.web.websocket.protocol.server.interview;

import me.codeleep.victor.web.websocket.protocol.BaseServerMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试流数据消息（服务端→客户端）。
 * <p>协议：{"type":"interview.stream_chunk","kind":"answer|thinking|tool_call|tool_result", ...}</p>
 * <ul>
 *   <li>kind=answer：最终回答增量，携带 text</li>
 *   <li>kind=thinking：推理思考增量，携带 text</li>
 *   <li>kind=tool_call：工具调用，携带 tool（name/args），前端渲染为可展开卡片</li>
 *   <li>kind=tool_result：工具结果，携带 tool（name/result），前端在对应卡片内展示结果</li>
 * </ul>
 */
public class InterviewServerStreamChunkMessage extends BaseServerMessage {

    private final String text;
    private final Kind kind;
    private final ToolData tool;

    public InterviewServerStreamChunkMessage(String text) {
        this(text, Kind.ANSWER, null);
    }

    public InterviewServerStreamChunkMessage(String text, Kind kind) {
        this(text, kind, null);
    }

    public InterviewServerStreamChunkMessage(String text, Kind kind, ToolData tool) {
        super("interview.stream_chunk");
        this.text = text;
        this.kind = kind != null ? kind : Kind.ANSWER;
        this.tool = tool;
    }

    @Override
    protected Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", getType());
        map.put("kind", kind.value());
        if (text != null) {
            map.put("text", text);
        }
        if (tool != null) {
            map.put("tool", tool.toMap());
        }
        return map;
    }

    /**
     * 流数据种类。
     */
    public enum Kind {
        ANSWER("answer"),
        THINKING("thinking"),
        TOOL_CALL("tool_call"),
        TOOL_RESULT("tool_result");

        private final String value;

        Kind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * 结构化工具数据，供前端卡片化展示工具调用（名称+参数）与结果。
     */
    public static class ToolData {
        private final String id;
        private final String name;
        private final Map<String, Object> args;
        private final String result;

        public ToolData(String name, Map<String, Object> args, String result) {
            this(null, name, args, result);
        }

        public ToolData(String id, String name, Map<String, Object> args, String result) {
            this.id = id;
            this.name = name;
            this.args = args;
            this.result = result;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (id != null) {
                m.put("id", id);
            }
            m.put("name", name);
            if (args != null) {
                m.put("args", args);
            }
            if (result != null) {
                m.put("result", result);
            }
            return m;
        }
    }
}