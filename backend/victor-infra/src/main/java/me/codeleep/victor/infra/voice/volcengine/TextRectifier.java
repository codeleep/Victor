package me.codeleep.victor.infra.voice.volcengine;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 文本整流器，用于控制文本发送到 TTS 的节奏。
 *
 * <p>核心作用是将外部输入的文本按一定节奏（intervalMs）逐条发送给 TTS 引擎，
 * 避免瞬间大量文本涌入导致服务端压力过大或音频播放拥堵。</p>
 */
@Slf4j
public class TextRectifier {
    private final BlockingQueue<String> textQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long intervalMs;
    private final Consumer<String> textSender;
    private Thread rectifierThread;

    public TextRectifier(long intervalMs, Consumer<String> textSender) {
        this.intervalMs = intervalMs;
        this.textSender = textSender;
    }

    public void start() {
        if (running.get()) {
            return;
        }
        running.set(true);

        rectifierThread = new Thread(() -> {
            try {
                while (running.get()) {
                    String text = textQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (text != null) {
                        try {
                            textSender.accept(text);
                        } catch (Exception e) {
                            log.error("Error sending text: {}", text, e);
                        }
                        if (intervalMs > 0 && !textQueue.isEmpty()) {
                            Thread.sleep(intervalMs);
                        }
                    }
                }
            } catch (InterruptedException e) {
                if (running.get()) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Text-Rectifier");
        rectifierThread.setDaemon(true);
        rectifierThread.start();
    }

    public void addText(String text) {
        if (!running.get()) {
            throw new IllegalStateException("Rectifier not started");
        }
        textQueue.offer(text);
    }

    public void addTextWithSplit(String text) {
        if (!running.get()) {
            throw new IllegalStateException("Rectifier not started");
        }
        String[] sentences = text.split("(?<=[。！？!?])");
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                textQueue.offer(sentence);
            }
        }
    }

    public void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        try {
            if (rectifierThread != null) {
                rectifierThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
