package me.codeleep.victor.infra.voice.volcengine.asr;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ASR协议消息，对应Main2中的二进制协议。
 *
 * <p>协议格式：[Header 4字节] [Sequence 4字节(可选)] [PayloadSize 4字节] [Payload]</p>
 * <p>Header: [ProtocolVersion|HeaderSize] [MessageType|Flags] [Serialization|Compression] [Reserved]</p>
 */
@Slf4j
@Data
public class AsrMessage {
    // 协议常量
    private static final byte PROTOCOL_VERSION = 0b0001;
    private static final byte DEFAULT_HEADER_SIZE = 0b0001;

    // Serialization Type
    private static final byte NO_SERIALIZATION = 0b0000;
    private static final byte JSON = 0b0001;

    // Compression Type
    private static final byte GZIP = 0b0001;

    private byte version = PROTOCOL_VERSION;
    private byte headerSize = DEFAULT_HEADER_SIZE;
    private AsrMsgType type;
    private byte flag;
    private byte serialization = JSON;
    private byte compression = GZIP;

    private int code;
    private int event;
    private boolean isLastPackage;
    private int sequence;
    private int payloadSize;
    private byte[] payload;

    public AsrMessage() {
    }

    public AsrMessage(AsrMsgType type, byte flag) {
        this.type = type;
        this.flag = flag;
    }

    /**
     * 从二进制数据解析ASR响应消息，对应Main2.parseResponse
     */
    public static AsrMessage unmarshal(byte[] res) throws Exception {
        if (res == null || res.length == 0) {
            return new AsrMessage();
        }

        ByteBuffer buffer = ByteBuffer.wrap(res);

        // 解析头部
        int versionAndHeaderSize = buffer.get() & 0xFF;
        byte version = (byte) ((versionAndHeaderSize >> 4) & 0x0F);
        byte headerSize = (byte) (versionAndHeaderSize & 0x0F);

        int typeAndFlag = buffer.get() & 0xFF;
        AsrMsgType type = AsrMsgType.fromValue((typeAndFlag >> 4) & 0x0F);
        byte flag = (byte) (typeAndFlag & 0x0F);

        int serialAndCompression = buffer.get() & 0xFF;
        byte serialization = (byte) ((serialAndCompression >> 4) & 0x0F);
        byte compression = (byte) (serialAndCompression & 0x0F);

        // Skip reserved byte
        buffer.get();

        AsrMessage message = new AsrMessage(type, flag);
        message.setVersion(version);
        message.setHeaderSize(headerSize);
        message.setSerialization(serialization);
        message.setCompression(compression);

        // 跳过header剩余padding
        int headerSizeInt = 4 * headerSize;
        int paddingSize = headerSizeInt - 4; // 已经读了4字节
        while (paddingSize > 0 && buffer.hasRemaining()) {
            buffer.get();
            paddingSize--;
        }

        // 解析flag字段
        if ((flag & 0x01) != 0 && buffer.remaining() >= 4) {
            message.setSequence(buffer.getInt());
        }
        if ((flag & 0x02) != 0) {
            message.setLastPackage(true);
        }
        if ((flag & 0x04) != 0 && buffer.remaining() >= 4) {
            message.setEvent(buffer.getInt());
        }

        // 解析messageType特定字段
        switch (type) {
            case SERVER_FULL_RESPONSE:
                if (buffer.remaining() >= 4) {
                    message.setPayloadSize(buffer.getInt());
                }
                break;
            case SERVER_ERROR_RESPONSE:
                if (buffer.remaining() >= 8) {
                    message.setCode(buffer.getInt());
                    message.setPayloadSize(buffer.getInt());
                }
                break;
        }

        // 读取剩余payload
        if (buffer.remaining() > 0) {
            byte[] payloadBytes = new byte[buffer.remaining()];
            buffer.get(payloadBytes);

            // 是否压缩
            if (compression == GZIP) {
                payloadBytes = gzipDecompress(payloadBytes);
            }

            message.setPayload(payloadBytes);
        }

        return message;
    }

    /**
     * 序列化消息为二进制，对应Main2中的协议构建逻辑
     */
    public byte[] marshal() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Write header
        buffer.write((version & 0x0F) << 4 | (headerSize & 0x0F));
        buffer.write((type.getValue() & 0x0F) << 4 | (flag & 0x0F));
        buffer.write((serialization & 0x0F) << 4 | (compression & 0x0F));
        buffer.write(0); // reserved

        // Write sequence if present
        if ((flag & 0x01) != 0 || (flag & 0x02) != 0) {
            buffer.write(ByteBuffer.allocate(4).putInt(sequence).array());
        }

        // Write event if present
        if ((flag & 0x04) != 0) {
            buffer.write(ByteBuffer.allocate(4).putInt(event).array());
        }

        // Write payload: always write payloadSize, even for empty payload
        byte[] payloadToWrite = (payload != null) ? payload : new byte[0];
        if (compression == GZIP) {
            payloadToWrite = gzipCompress(payloadToWrite);
        }
        buffer.write(ByteBuffer.allocate(4).putInt(payloadToWrite.length).array());
        if (payloadToWrite.length > 0) {
            buffer.write(payloadToWrite);
        }

        return buffer.toByteArray();
    }

    /**
     * 获取payload的文本内容（JSON反序列化后）
     */
    public String getPayloadMsg() {
        if (payload != null && payload.length > 0) {
            return new String(payload);
        }
        return null;
    }

    private static byte[] gzipCompress(byte[] src) {
        if (src == null || src.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(src, 0, src.length);
        } catch (IOException e) {
            log.error("GZIP compress error", e);
            return new byte[0];
        }
        return out.toByteArray();
    }

    private static byte[] gzipDecompress(byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream ins = new ByteArrayInputStream(src);
        try (GZIPInputStream gzip = new GZIPInputStream(ins)) {
            byte[] buffer = new byte[256];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            log.error("GZIP decompress error", e);
            return null;
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        if (type == AsrMsgType.SERVER_ERROR_RESPONSE) {
            return String.format("AsrMessage{type=%s, code=%d, event=%d, isLastPackage=%s, sequence=%d, payloadSize=%d, payloadMsg='%s'}",
                    type, code, event, isLastPackage, sequence, payloadSize, getPayloadMsg());
        }
        return String.format("AsrMessage{type=%s, event=%d, isLastPackage=%s, sequence=%d, payloadSize=%d, payloadMsg='%s'}",
                type, event, isLastPackage, sequence, payloadSize, getPayloadMsg());
    }
}
