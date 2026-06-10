package me.codeleep.victor.infra.voice.volcengine.asr;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 基于java-websocket的ASR WebSocket客户端，API风格对齐TtsWebSocketClient。
 */
@Slf4j
public class AsrWebSocketClient extends WebSocketClient {

    // Message Type Specific Flags（与Main2协议一致）
    private static final byte POS_SEQUENCE = 0b0001;
    private static final byte NEG_WITH_SEQUENCE = 0b0011;

    private final BlockingQueue<AsrMessage> messageQueue = new LinkedBlockingQueue<>();
    private volatile boolean connectionLost = false;

    public AsrWebSocketClient(URI serverUri, Map<String, String> headers) {
        super(serverUri);
        headers.forEach(this::addHeader);
    }

    public boolean isConnectionLost() {
        return connectionLost;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        connectionLost = false;
        log.info("ASR WebSocket connection established, Logid: {}", handshakedata.getFieldValue("x-tt-logid"));
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            AsrMessage message = AsrMessage.unmarshal(data);
            messageQueue.put(message);
        } catch (Exception e) {
            log.error("Failed to parse ASR message", e);
        }
    }

    @Override
    public void onMessage(String message) {
        log.warn("Received unexpected text message: {}", message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connectionLost = true;
        log.info("ASR WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("ASR WebSocket error", ex);
    }

    /**
     * 发送完整客户端请求（初始配置），对应Main2.sendFullClientRequest
     */
    public void sendFullClientRequest(byte[] payload, int seq) throws Exception {
        AsrMessage message = new AsrMessage(AsrMsgType.CLIENT_FULL_REQUEST, POS_SEQUENCE);
        message.setSequence(seq);
        message.setPayload(payload);
        sendMessage(message);
    }

    /**
     * 发送音频分段，对应Main2.sendAudioSegment
     * 音频数据不使用GZIP压缩（compression=0）
     */
    public void sendAudioSegment(byte[] audioData, boolean isLast, int seq) throws Exception {
        byte flag = isLast ? NEG_WITH_SEQUENCE : POS_SEQUENCE;
        AsrMessage message = new AsrMessage(AsrMsgType.CLIENT_AUDIO_ONLY_REQUEST, flag);
        message.setSequence(seq);
        message.setPayload(audioData);
        message.setSerialization((byte) 0b0000); // NO_SERIALIZATION
        message.setCompression((byte) 0b0000);   // NO_COMPRESSION
        sendMessage(message);
    }

    /**
     * 发送消息，对齐TtsWebSocketClient.sendMessage
     */
    public void sendMessage(AsrMessage message) throws Exception {
        send(message.marshal());
    }

    /**
     * 阻塞接收消息，对齐TtsWebSocketClient.receiveMessage
     */
    public AsrMessage receiveMessage() throws InterruptedException {

        return messageQueue.take();
    }

    /**
     * 带超时地接收消息，对齐TtsWebSocketClient.pollMessage
     */
    public AsrMessage pollMessage(long timeoutMs) throws InterruptedException {
        return messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
