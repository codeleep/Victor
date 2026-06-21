package me.codeleep.victor.web.websocket.processor;

import me.codeleep.victor.web.websocket.protocol.server.interview.InterviewServerStreamChunkMessage;

/**
 * 流式片段，携带种类（answer/thinking/tool_call/tool_result）与可选工具结构数据。
 *
 * <p>处理器产出此对象，由 {@code InterviewSession} 转成带 kind 的 chunk 消息发给前端。
 * thinking/tool 仅供前端实时展示以降低候选人感知等待，不参与落库与 TTS。</p>
 *
 * @param text 文本内容（answer/thinking 增量）
 * @param kind 片段种类
 * @param tool 结构化工具数据（tool_call/tool_result 时填充）
 */
public record StreamChunk(String text,
                          InterviewServerStreamChunkMessage.Kind kind,
                          InterviewServerStreamChunkMessage.ToolData tool) {

    public StreamChunk(String text) {
        this(text, InterviewServerStreamChunkMessage.Kind.ANSWER, null);
    }

    public StreamChunk(String text, InterviewServerStreamChunkMessage.Kind kind) {
        this(text, kind, null);
    }

    public StreamChunk {
        if (kind == null) {
            kind = InterviewServerStreamChunkMessage.Kind.ANSWER;
        }
    }
}