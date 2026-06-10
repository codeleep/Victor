package me.codeleep.victor.infra.voice.volcengine.tts;

import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.infra.voice.volcengine.protocol.EventType;
import me.codeleep.victor.infra.voice.volcengine.protocol.MsgTypeFlagBits;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class TtsWebSocketClient extends WebSocketClient {
    private final BlockingQueue<TtsMessage> messageQueue = new LinkedBlockingQueue<>();

    public TtsWebSocketClient(URI serverUri, Map<String, String> headers) {
        super(serverUri, headers);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("WebSocket connection established, Logid: {}", handshakedata.getFieldValue("x-tt-logid"));
    }

    @Override
    public void onMessage(String message) {
        log.warn("Received unexpected text message: {}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            TtsMessage message = TtsMessage.unmarshal(bytes.array());
            messageQueue.put(message);
        } catch (Exception e) {
            log.error("Failed to parse message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("WebSocket connection closed: code={}, reason={}, remote={}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error", ex);
    }

    public void sendStartConnection() throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.START_CONNECTION);
        message.setPayload("{}".getBytes());
        sendMessage(message);
    }

    public void sendFinishConnection() throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.FINISH_CONNECTION);
        sendMessage(message);
    }

    public void sendStartSession(byte[] payload, String sessionId) throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.START_SESSION);
        message.setSessionId(sessionId);
        message.setPayload(payload);
        sendMessage(message);
    }

    public void sendFinishSession(String sessionId) throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.FINISH_SESSION);
        message.setSessionId(sessionId);
        message.setPayload("{}".getBytes());
        sendMessage(message);
    }

    public void sendTaskRequest(byte[] payload, String sessionId) throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.WITH_EVENT);
        message.setEvent(EventType.TASK_REQUEST);
        message.setSessionId(sessionId);
        message.setPayload(payload);
        sendMessage(message);
    }

    public void sendFullClientMessage(byte[] payload) throws Exception {
        TtsMessage message = new TtsMessage(TtsMsgType.FULL_CLIENT_REQUEST, MsgTypeFlagBits.NO_SEQ);
        message.setPayload(payload);
        sendMessage(message);
    }

    public void sendMessage(TtsMessage message) throws Exception {
        log.info("Send: {}", message);
        send(message.marshal());
    }

    public TtsMessage receiveMessage() throws InterruptedException {
        return messageQueue.take();
    }

    public TtsMessage pollMessage(long timeoutMs) throws InterruptedException {
        return messageQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public TtsMessage waitForMessage(TtsMsgType type, EventType event) throws InterruptedException {
        while (true) {
            TtsMessage message = receiveMessage();
            if (message.getType() == type && message.getEvent() == event) {
                return message;
            } else {
                throw new RuntimeException("Unexpected message: " + message);
            }
        }
    }
}