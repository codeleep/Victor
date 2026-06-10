package me.codeleep.victor.web.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.client.asr.AsrClientStreamEndMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientReconnectMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStartMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStopMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.client.interview.InterviewClientStreamEndMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientCancelMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientInterruptMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamBeginMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamChunkMessage;
import me.codeleep.victor.web.websocket.protocol.client.tts.TtsClientStreamEndMessage;

/**
 * 客户端消息基类，提供 Jackson 多态反序列化。
 */
@Setter
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true, defaultImpl = UnknownMessage.class)
@JsonSubTypes({
        // Interview 命令
        @JsonSubTypes.Type(value = InterviewClientStartMessage.class, name = "interview.start"),
        @JsonSubTypes.Type(value = InterviewClientReconnectMessage.class, name = "interview.reconnect"),
        @JsonSubTypes.Type(value = InterviewClientStopMessage.class, name = "interview.stop"),
        @JsonSubTypes.Type(value = InterviewClientStreamBeginMessage.class, name = "interview.stream_begin"),
        @JsonSubTypes.Type(value = InterviewClientStreamChunkMessage.class, name = "interview.stream_chunk"),
        @JsonSubTypes.Type(value = InterviewClientStreamEndMessage.class, name = "interview.stream_end"),
        @JsonSubTypes.Type(value = InterviewClientInterruptMessage.class, name = "interview.interrupt"),
        // ASR 命令
        @JsonSubTypes.Type(value = AsrClientStreamBeginMessage.class, name = "asr.stream_begin"),
        @JsonSubTypes.Type(value = AsrClientStreamChunkMessage.class, name = "asr.stream_chunk"),
        @JsonSubTypes.Type(value = AsrClientStreamEndMessage.class, name = "asr.stream_end"),
        @JsonSubTypes.Type(value = AsrClientInterruptMessage.class, name = "asr.interrupt"),
        // TTS 命令
        @JsonSubTypes.Type(value = TtsClientStreamBeginMessage.class, name = "tts.stream_begin"),
        @JsonSubTypes.Type(value = TtsClientStreamChunkMessage.class, name = "tts.stream_chunk"),
        @JsonSubTypes.Type(value = TtsClientStreamEndMessage.class, name = "tts.stream_end"),
        @JsonSubTypes.Type(value = TtsClientCancelMessage.class, name = "tts.cancel"),
        @JsonSubTypes.Type(value = TtsClientInterruptMessage.class, name = "tts.interrupt"),
        // fallback
        @JsonSubTypes.Type(value = UnknownMessage.class, name = "unknown")
})
public abstract class BaseClientMessage implements ClientMessage {

    private final String type;

    @Getter
    @Setter
    private long clientTimestamp;

    @Getter
    @Setter
    private long serverTimestamp;

    protected BaseClientMessage() {
        this.type = "unknown";
    }

    protected BaseClientMessage(String type) {
        this.type = type;
    }
}
