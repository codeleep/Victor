package me.codeleep.victor.infra.voice.tts;

/**
 * TTS（文本转语音）客户端接口。
 *
 * <p>一个 TtsClient 代表一个到 TTS 服务的连接，在该连接上可以创建多个 {@link TtsSession}，
 * 每个 Session 独立进行一次文本转语音任务，互不干扰。</p>
 *
 * <h3>典型用法 - 流式合成：</h3>
 * <pre>{@code
 * TtsClient client = ...;
 * client.connect();
 *
 * try (TtsSession session = client.createSession()) {
 *     session.speakText("你好，");
 *     session.speakText("世界");
 *     session.finish();
 *
 *     while (!session.isFinished()) {
 *         byte[] audio = session.pollAudio(5000);
 *         if (audio != null) {
 *             // 实时播放音频
 *         }
 *     }
 * } finally {
 *     client.disconnect();
 * }
 * }</pre>
 *
 * <h3>典型用法 - 一次性合成：</h3>
 * <pre>{@code
 * TtsClient client = ...;
 * client.connect();
 *
 * byte[] audio = client.synthesize("你好，世界");
 * // 播放完整音频
 *
 * client.disconnect();
 * }</pre>
 */
public interface TtsClient {

    /**
     * 建立 TTS 服务连接并完成初始化握手。
     *
     * <p>如果已经连接则直接返回。</p>
     *
     * @throws Exception 连接失败或握手超时
     */
    void connect() throws Exception;

    /**
     * 创建一个新的 TTS 合成会话，用于流式合成。
     *
     * <p>每个 Session 对应一次独立的文本转语音任务。创建后自动启动，
     * 可多次调用 {@link TtsSession#speakText(String)} 流式发送文本。</p>
     *
     * @return 已启动的 TtsSession 实例
     * @throws Exception 未连接时抛出 IllegalStateException，Session 启动失败时抛出异常
     */
    TtsSession createSession() throws Exception;

    /**
     * 一次性合成：发送完整文本并等待所有音频数据。
     *
     * <p>内部创建 Session、发送文本、收集所有音频后自动关闭。
     * 适合不需要流式播放的简单场景。</p>
     *
     * @param text 待合成的文本
     * @return 合成的完整音频数据
     * @throws Exception 合成失败
     */
    byte[] synthesize(String text) throws Exception;

    /**
     * 断开 TTS 服务连接。
     *
     * <p>会先关闭所有活跃的 Session，然后断开底层连接。</p>
     *
     * @throws Exception 断开过程中发生错误
     */
    void disconnect() throws Exception;

    /**
     * 检查客户端是否已连接。
     *
     * @return 已连接返回 true
     */
    boolean isConnected();
}
