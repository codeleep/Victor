package me.codeleep.victor.infra.voice.asr;

/**
 * ASR（自动语音识别）客户端接口。
 *
 * <p>一个 AsrClient 代表一个到 ASR 服务的连接，在该连接上可以创建多个 {@link AsrSession}，
 * 每个 Session 独立进行一次语音识别任务，互不干扰。</p>
 *
 * <h3>典型用法 - 流式识别：</h3>
 * <pre>{@code
 * AsrClient client = ...;
 * client.connect();
 *
 * try (AsrSession session = client.createSession()) {
 *     // 流式发送音频
 *     session.sendAudio(chunk1);
 *     session.sendAudio(chunk2);
 *     session.finishAudio();
 *
 *     // 流式获取结果
 *     while (!session.isFinished()) {
 *         AsrResult result = session.pollResult(5000);
 *         if (result != null) {
 *             System.out.println(result.getText());
 *         }
 *     }
 * } finally {
 *     client.disconnect();
 * }
 * }</pre>
 *
 * <h3>典型用法 - 一次性识别：</h3>
 * <pre>{@code
 * AsrClient client = ...;
 * client.connect();
 *
 * AsrResult result = client.recognize(audioData);
 * System.out.println(result.getText());
 *
 * client.disconnect();
 * }</pre>
 */
public interface AsrClient {

    /**
     * 建立 ASR 服务连接并完成初始化握手。
     *
     * <p>如果已经连接则直接返回。</p>
     *
     * @throws Exception 连接失败或握手超时
     */
    void connect() throws Exception;

    /**
     * 创建一个新的 ASR 识别会话，用于流式识别。
     *
     * <p>每个 Session 对应一次独立的语音识别任务。创建后自动启动，
     * 可多次调用 {@link AsrSession#sendAudio(byte[])} 流式发送音频。</p>
     *
     * @return 已启动的 AsrSession 实例
     * @throws Exception 未连接时抛出 IllegalStateException，Session 启动失败时抛出异常
     */
    AsrSession createSession() throws Exception;

    /**
     * 一次性识别：发送完整音频数据并等待最终结果。
     *
     * <p>内部创建 Session、发送音频、等待最终识别结果后自动关闭。
     * 适合不需要流式的简单场景。</p>
     *
     * @param audioData 完整的音频数据
     * @return 最终识别结果
     * @throws Exception 识别失败
     */
    AsrResult recognize(byte[] audioData) throws Exception;

    /**
     * 断开 ASR 服务连接。
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
