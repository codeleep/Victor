package me.codeleep.victor.infra.voice.asr;

/**
 * ASR（自动语音识别）会话接口。
 *
 * <p>每个 AsrSession 代表一次独立的语音识别任务，支持流式输入音频、流式输出识别结果。</p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>创建后自动启动（由 {@link AsrClient#createSession()} 完成）</li>
 *   <li>{@link #sendAudio(byte[])} — 流式发送音频数据（可多次调用）</li>
 *   <li>{@link #finishAudio()} — 通知服务端音频已全部发送</li>
 *   <li>{@link #pollResult(long)} / {@link #takeResult()} — 流式获取识别结果</li>
 *   <li>{@link #close()} — 关闭会话（支持 try-with-resources）</li>
 * </ol>
 *
 * <h3>流式示例：</h3>
 * <pre>{@code
 * try (AsrSession session = client.createSession()) {
 *     // 边录音边发送
 *     session.sendAudio(chunk1);
 *     AsrResult partial = session.pollResult(100);  // 可能得到中间结果
 *     session.sendAudio(chunk2);
 *     session.finishAudio();
 *
 *     // 收集最终结果
 *     while (!session.isFinished()) {
 *         AsrResult result = session.pollResult(5000);
 *         if (result != null && result.isFinal()) {
 *             System.out.println(result.getText());
 *         }
 *     }
 * }
 * }</pre>
 */
public interface AsrSession extends AutoCloseable {

    /**
     * 流式发送音频数据到服务端。
     *
     * <p>可在同一个 Session 中多次调用，每次发送一段音频片段。
     * 服务端可能在音频发送过程中就返回中间识别结果。</p>
     *
     * @param audioData 音频数据片段
     * @throws IllegalStateException 会话未激活或已结束
     * @throws Exception             发送失败
     */
    void sendAudio(byte[] audioData) throws Exception;

    /**
     * 通知服务端音频已全部发送完毕。
     *
     * <p>发送后服务端会将最终识别结果返回并标记会话结束。
     * 调用后不能再调用 {@link #sendAudio(byte[])}。</p>
     *
     * @throws Exception 发送失败或会话状态异常
     */
    void finishAudio() throws Exception;

    /**
     * 阻塞获取一条识别结果（中间结果或最终结果）。
     *
     * <p>如果队列为空则阻塞等待，直到有结果或会话结束。
     * 返回 null 表示会话已结束。</p>
     *
     * @return 识别结果，或 null（会话结束）
     * @throws InterruptedException 等待被中断
     */
    AsrResult takeResult() throws InterruptedException;

    /**
     * 带超时地获取一条识别结果。
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 识别结果，超时或会话结束时返回 null
     * @throws InterruptedException 等待被中断
     */
    AsrResult pollResult(long timeoutMs) throws InterruptedException;

    /**
     * 检查结果队列中是否还有待消费的数据。
     *
     * @return 有待消费数据返回 true
     */
    boolean hasResult();

    /**
     * 设置音频已发送完毕。
     */
    void audioFinished();

    /**
     * 检查音频是否已发送完毕。
     *
     * <p>调用 {@link #finishAudio()} 后返回 true。</p>
     *
     * @return 音频已发送完毕返回 true
     */
    boolean isAudioFinished();

    /**
     * 检查会话是否已结束（收到最终结果或 ERROR）。
     *
     * @return 已结束返回 true
     */
    boolean isFinished();

    /**
     * 关闭会话，释放资源。
     *
     * <p>如果尚未调用 {@link #finishAudio()} 则自动调用，然后从 Client 中注销此 Session。</p>
     *
     * @throws Exception 关闭过程中发生错误
     */
    @Override
    void close() throws Exception;
}
