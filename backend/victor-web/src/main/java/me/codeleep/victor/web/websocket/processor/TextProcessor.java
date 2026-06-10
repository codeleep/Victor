package me.codeleep.victor.web.websocket.processor;

import reactor.core.publisher.Flux;

/**
 * 文本处理器接口。
 *
 * <p>处理ASR识别后的文本，返回句子流。</p>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>LLM对话（边生成边播放）</li>
 *   <li>复读机（直接返回原文）</li>
 *   <li>实时翻译</li>
 * </ul>
 *
 * <h3>示例：</h3>
 * <pre>
 * {@code
 * // LLM处理器
 * @Component
 * public class LlmTextProcessor implements TextProcessor {
 *     @Override
 *     public Flux<String> process(ProcessingContext context, String text) {
 *         return chatModel.stream(prompt)
 *             .map(response -> response.getResult().getOutput().getText())
 *             .filter(chunk -> chunk != null && !chunk.isEmpty())
 *             .windowUntil(this::isCompleteSentence)
 *             .flatMap(window -> window.collectList()
 *                 .map(chunks -> String.join("", chunks)));
 *     }
 * }
 *
 * // 复读机处理器
 * @Component
 * public class EchoTextProcessor implements TextProcessor {
 *     @Override
 *     public Flux<String> process(ProcessingContext context, String text) {
 *         return Flux.just(text);
 *     }
 * }
 * }
 * </pre>
 */
public interface TextProcessor {

    /**
     * 流式处理方法。
     *
     * <p>返回句子流，每个元素是一个完整的句子，可以直接发送给TTS。</p>
     *
     * @param context 处理上下文
     * @param text    ASR识别的原始文本
     * @return 句子流，每个元素是一个完整句子
     */
    Flux<String> process(ProcessingContext context, String text);

    /**
     * 获取处理器名称。
     *
     * @return 处理器名称，用于日志和配置
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
