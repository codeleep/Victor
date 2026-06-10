package me.codeleep.victor.web.websocket.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 复读机文本处理器。
 *
 * <p>将ASR识别的文本原样返回，交给TTS合成。</p>
 */
@Component
@ConditionalOnProperty(name = "speech.processor", havingValue = "echo", matchIfMissing = true)
public class EchoTextProcessor implements TextProcessor {

    @Override
    public Flux<String> process(ProcessingContext context, String text) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }
        return Flux.just(text);
    }

    @Override
    public String getName() {
        return "echo";
    }
}
