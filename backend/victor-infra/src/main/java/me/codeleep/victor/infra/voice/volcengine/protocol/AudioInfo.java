package me.codeleep.victor.infra.voice.volcengine.protocol;

/**
 * @author: codeleep
 * @createTime: 2026/05/23 17:54
 * @description:
 */
public class AudioInfo {
    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;

    public AudioInfo(int sampleRate, int channels, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
    }
}
