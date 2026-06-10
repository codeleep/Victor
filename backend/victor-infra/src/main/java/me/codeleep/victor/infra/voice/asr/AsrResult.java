package me.codeleep.victor.infra.voice.asr;

/**
 * ASR 识别结果。
 *
 * <p>包含识别文本及元数据。流式识别场景下会产生多条结果：
 * 中间结果（isFinal=false）表示部分识别，最终结果（isFinal=true）表示完整识别。</p>
 */
public class AsrResult {

    /** 识别文本 */
    private final String text;
    /** 是否为最终结果 */
    private final boolean isFinal;
    /** 结果序号 */
    private final int sequence;

    public AsrResult(String text, boolean isFinal, int sequence) {
        this.text = text;
        this.isFinal = isFinal;
        this.sequence = sequence;
    }

    public String getText() {
        return text;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public int getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return "AsrResult{text='" + text + "', isFinal=" + isFinal + ", sequence=" + sequence + '}';
    }
}
