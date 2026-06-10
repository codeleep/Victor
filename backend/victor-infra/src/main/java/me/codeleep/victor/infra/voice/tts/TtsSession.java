package me.codeleep.victor.infra.voice.tts;

/**
 * TTS（文本转语音）会话接口。
 *
 * <p>每个 TtsSession 代表一次独立的文本转语音任务，支持流式输入文本、流式输出音频。</p>
 *
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>创建后自动启动（由 {@link TtsClient#createSession()} 完成）</li>
 *   <li>{@link #speakText(String)} — 流式发送待合成的文本（可多次调用）</li>
 *   <li>{@link #pollAudio(long)} / {@link #takeAudio()} — 流式获取合成的音频数据</li>
 *   <li>{@link #finish()} — 通知服务端文本已全部发送</li>
 *   <li>{@link #close()} — 关闭会话（支持 try-with-resources）</li>
 * </ol>
 *
 * <h3>流式示例：</h3>
 * <pre>{@code
 * try (TtsSession session = client.createSession()) {
 *     // 边生成文本边发送
 *     session.speakText("你好，");
 *     byte[] audio1 = session.pollAudio(1000);  // 可能已得到部分音频
 *     session.speakText("世界");
 *     session.finish();
 *
 *     // 收集剩余音频
 *     while (!session.isFinished()) {
 *         byte[] audio = session.pollAudio(5000);
 *         if (audio != null) {
 *             // 实时播放
 *         }
 *     }
 * }
 * }</pre>
 */
public interface TtsSession extends AutoCloseable {

    /**
     * 流式发送待合成的文本。
     *
     * <p>可在同一个 Session 中多次调用，每次发送一段文本。
     * 文本发送后，服务端会异步返回对应的音频数据，通过 {@link #pollAudio} 流式获取。</p>
     *
     * @param text 待合成的文本内容
     * @throws IllegalStateException 会话未激活或已结束
     * @throws Exception             发送失败
     */
    void speakText(String text) throws Exception;

    /**
     * 通知服务端文本已全部发送完毕。
     *
     * <p>发送后服务端会将剩余音频全部返回，最后发送结束确认。
     * 调用后不能再调用 {@link #speakText(String)}。</p>
     *
     * @throws Exception 发送失败或会话状态异常
     */
    void finish() throws Exception;

    /**
     * 阻塞获取一条音频数据。
     *
     * <p>如果队列为空则阻塞等待，直到有数据或会话结束信号。
     * 返回 null 表示会话已结束。</p>
     *
     * @return 音频数据，或 null（会话结束）
     * @throws InterruptedException 等待被中断
     */
    byte[] takeAudio() throws InterruptedException;

    /**
     * 带超时地获取一条音频数据。
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return 音频数据，超时或会话结束时返回 null
     * @throws InterruptedException 等待被中断
     */
    byte[] pollAudio(long timeoutMs) throws InterruptedException;

    /**
     * 检查音频队列中是否还有待消费的数据。
     *
     * @return 有待消费数据返回 true
     */
    boolean hasAudio();

    /**
     * 设置文本是否已发送完毕。
     *
     * <p>调用 {@link #finish()} 后返回 true。</p>
     *
     * @return 文本已发送完毕返回 true
     */
    void textFinished();

    /**
     * 检查文本是否已发送完毕。
     *
     * <p>调用 {@link #finish()} 后返回 true。</p>
     *
     * @return 文本已发送完毕返回 true
     */
    boolean isTextFinished();

    /**
     * 检查会话是否已结束（收到结束确认或 ERROR）。
     *
     * @return 已结束返回 true
     */
    boolean isFinished();

    /**
     * 关闭会话，释放资源。
     *
     * <p>如果尚未调用 {@link #finish()} 则自动调用，然后从 Client 中注销此 Session。</p>
     *
     * @throws Exception 关闭过程中发生错误
     */
    @Override
    void close() throws Exception;
}
