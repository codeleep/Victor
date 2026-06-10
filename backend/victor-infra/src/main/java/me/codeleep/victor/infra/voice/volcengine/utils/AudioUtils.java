package me.codeleep.victor.infra.voice.volcengine.utils;

import me.codeleep.victor.infra.voice.volcengine.protocol.AudioInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: codeleep
 * @createTime: 2026/05/23 17:54
 * @description:
 */
public class AudioUtils {

    /**
     * 检查是否为WAV格式
     */
    public static boolean isWavFormat(byte[] data) {
        if (data.length < 44) {
            return false;
        }
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
                data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    /**
     * 读取音频文件数据
     */
    public static byte[] readAudioData(String audioPath) throws IOException {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            throw new FileNotFoundException("Audio file not found: " + audioPath);
        }

        // 读取完整文件内容
        try (FileInputStream fis = new FileInputStream(audioFile);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    /**
     * 解析WAV文件信息
     */
    public static AudioInfo parseWavInfo(byte[] data) {
        if (!isWavFormat(data)) {
            throw new IllegalArgumentException("Not a valid WAV file");
        }

        // 使用小端序解析WAV头
        // 声道数：偏移量22-23
        int channels = ((data[23] & 0xFF) << 8) | (data[22] & 0xFF);

        // 采样率：偏移量24-27
        int sampleRate = ((data[27] & 0xFF) << 24) | ((data[26] & 0xFF) << 16) |
                ((data[25] & 0xFF) << 8) | (data[24] & 0xFF);

        // 位深度：偏移量34-35
        int bitsPerSample = ((data[35] & 0xFF) << 8) | (data[34] & 0xFF);

        System.out.println("解析WAV信息: 声道数=" + channels + ", 采样率=" + sampleRate + ", 位深度=" + bitsPerSample);

        return new AudioInfo(sampleRate, channels, bitsPerSample);
    }

    /**
     * 计算分段大小
     */
    public static int calculateSegmentSize(AudioInfo audioInfo, int segmentDurationMs) {
        // 与Go版本保持一致：sampwidth = bitsPerSample / 8
        int sampWidth = audioInfo.bitsPerSample / 8;
        int bytesPerSec = audioInfo.channels * sampWidth * audioInfo.sampleRate;
        int segmentSize = bytesPerSec * segmentDurationMs / 1000;
        System.out.println("计算分段大小: 声道数=" + audioInfo.channels +
                ", 采样宽度=" + sampWidth +
                ", 采样率=" + audioInfo.sampleRate +
                ", 分段大小=" + segmentSize + " 字节");
        return segmentSize;
    }

    /**
     * 提取WAV文件的纯音频数据
     */
    public static byte[] extractWavAudioData(byte[] wavData) {
        if (!isWavFormat(wavData)) {
            throw new IllegalArgumentException("Not a valid WAV file");
        }

        // 查找data子块
        int pos = 36;
        while (pos < wavData.length - 8) {
            // 检查是否为data子块
            if (wavData[pos] == 'd' && wavData[pos + 1] == 'a' &&
                    wavData[pos + 2] == 't' && wavData[pos + 3] == 'a') {

                // 读取data子块大小（小端序）
                int dataSize = ((wavData[pos + 7] & 0xFF) << 24) |
                        ((wavData[pos + 6] & 0xFF) << 16) |
                        ((wavData[pos + 5] & 0xFF) << 8) |
                        (wavData[pos + 4] & 0xFF);

                System.out.println("找到data子块，大小: " + dataSize + " 字节");

                // 提取音频数据
                byte[] audioData = new byte[dataSize];
                System.arraycopy(wavData, pos + 8, audioData, 0, dataSize);

                return audioData;
            }
            pos++;
        }

        throw new IllegalArgumentException("No data subchunk found in WAV file");
    }

    /**
     * 将裸PCM数据封装为完整的WAV文件（添加44字节WAV头）。
     *
     * @param pcm          裸PCM音频数据
     * @param sampleRate   采样率（如24000）
     * @param bitsPerSample 位深度（如16）
     * @param channels     声道数（1=单声道，2=立体声）
     * @return 带WAV头的完整音频数据
     */
    public static byte[] wrapPcmWithWavHeader(byte[] pcm, int sampleRate, int bitsPerSample, int channels) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        byte[] wav = new byte[44 + dataSize];
        // RIFF
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        writeIntLE(wav, 4, 36 + dataSize);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        // fmt
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        writeIntLE(wav, 16, 16);
        writeShortLE(wav, 20, (short) 1); // PCM format
        writeShortLE(wav, 22, (short) channels);
        writeIntLE(wav, 24, sampleRate);
        writeIntLE(wav, 28, byteRate);
        writeShortLE(wav, 32, (short) blockAlign);
        writeShortLE(wav, 34, (short) bitsPerSample);
        // data
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        writeIntLE(wav, 40, dataSize);
        System.arraycopy(pcm, 0, wav, 44, dataSize);
        return wav;
    }

    /**
     * 根据音频编码格式返回对应的HTTP Content-Type。
     *
     * @param encoding 音频编码格式（wav/pcm/mp3等）
     * @return 对应的Content-Type字符串
     */
    public static String contentTypeForEncoding(String encoding) {
        if (encoding == null) {
            return "audio/wav";
        }
        return switch (encoding.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "ogg", "opus" -> "audio/ogg";
            case "wav", "pcm" -> "audio/wav";
            default -> "audio/wav";
        };
    }

    private static void writeIntLE(byte[] b, int off, int val) {
        b[off] = (byte) val; b[off + 1] = (byte) (val >> 8); b[off + 2] = (byte) (val >> 16); b[off + 3] = (byte) (val >> 24);
    }

    private static void writeShortLE(byte[] b, int off, short val) {
        b[off] = (byte) val; b[off + 1] = (byte) (val >> 8);
    }

    /**
     * 分割音频数据
     */
    public static List<byte[]> splitAudio(byte[] audioData, int segmentSize) {
        List<byte[]> segments = new ArrayList<>();
        for (int offset = 0; offset < audioData.length; offset += segmentSize) {
            int len = Math.min(segmentSize, audioData.length - offset);
            byte[] segment = new byte[len];
            System.arraycopy(audioData, offset, segment, 0, len);
            segments.add(segment);
        }
        return segments;
    }
}
