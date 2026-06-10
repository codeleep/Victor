package me.codeleep.victor.infra.voice.volcengine.tts;

import lombok.Getter;

@Getter
public enum TtsMsgType {
    INVALID((byte) 0),
    FULL_CLIENT_REQUEST((byte) 0b1),
    AUDIO_ONLY_CLIENT((byte) 0b10),
    FULL_SERVER_RESPONSE((byte) 0b1001),
    AUDIO_ONLY_SERVER((byte) 0b1011),
    FRONT_END_RESULT_SERVER((byte) 0b1100),
    ERROR((byte) 0b1111);

    private final byte value;

    TtsMsgType(byte value) {
        this.value = value;
    }

    public static TtsMsgType fromValue(int value) {
        for (TtsMsgType type : TtsMsgType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MsgType value: " + value);
    }
}