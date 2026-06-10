package me.codeleep.victor.web.websocket.session;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 中断响应器，用于检测异步操作是否已被客户端中断。
 *
 * <p>维护一个固定大小的中断事件队列，通过事件序列号判断某个异步操作
 * （如 LLM 生成、ASR 结果收集、TTS 音频收集）是否在执行期间收到了中断信号。</p>
 *
 * <h3>使用方式：</h3>
 * <ol>
 *   <li>异步任务开始前调用 {@link #startProcessing(String)} 注册处理 ID</li>
 *   <li>收到客户端中断命令时调用 {@link #record(long, long, String)} 记录中断事件</li>
 *   <li>异步任务执行过程中周期性调用 {@link #isInterrupted(String, long, String)} 检查是否被中断</li>
 * </ol>
 */
public class InterruptResponder {

    private static final int MAX_SIZE = 5;

    /**
     * 中断事件记录。
     *
     * @param serverTimestamp 服务端收到中断时的时间戳
     * @param eventSequence   全局递增的事件序列号
     * @param type            中断类型，如 "LLM_RESPONSE"、"ASR_RESPONSE"、"TTS_RESPONSE"、"ALL"
     */
    private record InterruptEvent(
            long serverTimestamp,
            long eventSequence,
            String type
    ) {
    }

    private final Deque<InterruptEvent> events = new ArrayDeque<>(MAX_SIZE);
    private volatile String currentProcessingId;

    /**
     * 注册当前正在执行的异步处理任务。
     *
     * @param processingId 处理任务的唯一标识
     */
    public void startProcessing(String processingId) {
        currentProcessingId = processingId;
    }

    /**
     * 记录一条中断事件。
     *
     * @param serverTimestamp 服务端时间戳
     * @param eventSequence   事件序列号
     * @param type            中断类型
     */
    public synchronized void record(long serverTimestamp, long eventSequence, String type) {
        if (events.size() == MAX_SIZE) {
            events.removeFirst();
        }
        events.addLast(new InterruptEvent(serverTimestamp, eventSequence, type));
    }

    /**
     * 判断指定操作是否已被中断。
     *
     * <p>中断条件（满足任一即为中断）：</p>
     * <ul>
     *   <li>当前处理 ID 已变更（新的处理任务已开始）</li>
     *   <li>队列中存在序列号大于当前操作、且类型匹配的中断事件</li>
     * </ul>
     *
     * @param processingId      当前操作的处理 ID
     * @param operationSequence 当前操作的事件序列号
     * @param acceptedType      接受的中断类型（"ALL" 匹配所有类型）
     * @return true 表示已被中断
     */
    public boolean isInterrupted(String processingId, long operationSequence, String acceptedType) {
        if (processingId != null && !processingId.equals(currentProcessingId)) {
            return true;
        }
        return hasMatchingInterrupt(operationSequence, acceptedType);
    }

    private synchronized boolean hasMatchingInterrupt(long operationSequence, String acceptedType) {
        return events.stream().anyMatch(event ->
                event.eventSequence() > operationSequence && ("ALL".equals(event.type()) || event.type().equals(acceptedType))
        );
    }
}
