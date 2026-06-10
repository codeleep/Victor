package me.codeleep.victor.infra.voice.volcengine.asr;

import lombok.Getter;

/**
 * ASR协议消息类型，对应Main2中的协议常量。
 */
@Getter
public enum AsrMsgType {
    CLIENT_FULL_REQUEST((byte) 0b0001),
    CLIENT_AUDIO_ONLY_REQUEST((byte) 0b0010),
    SERVER_FULL_RESPONSE((byte) 0b1001),
    SERVER_ERROR_RESPONSE((byte) 0b1111);

    private final byte value;

    AsrMsgType(byte value) {
        this.value = value;
    }

    public static AsrMsgType fromValue(int value) {
        for (AsrMsgType type : AsrMsgType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AsrMsgType value: " + value);
    }
}
