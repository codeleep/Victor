package me.codeleep.victor.web.websocket.processor;

import reactor.core.publisher.Flux;

/**
 * 文本处理器接口。
 *
 * <p>处理ASR识别后的文本，返回带种类的片段流（answer/thinking/tool）。
 * answer 片段用于落库与 TTS；thinking/tool 仅供前端实时展示。</p>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>LLM对话（边生成边播放）</li>
 *   <li>复读机（直接返回原文）</li>
 *   <li>实时翻译</li>
 * </ul>
 */
public interface TextProcessor {

    /**
     * 流式处理方法。
     *
     * <p>返回片段流，每个元素携带文本与种类。answer 片段建议为完整句子以便 TTS 合成。</p>
     *
     * @param context 处理上下文
     * @param text    ASR识别的原始文本
     * @return 片段流
     */
    Flux<StreamChunk> process(ProcessingContext context, String text);

    /**
     * 获取处理器名称。
     *
     * @return 处理器名称，用于日志和配置
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}