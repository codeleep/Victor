package me.codeleep.victor.web.websocket.protocol.client.interview;

import lombok.Data;

/**
 * 面试输入附件。
 *
 * <p>附件随 stream_chunk 一次性发送完整内容，不参与文本流式拼接。</p>
 */
@Data
public class InterviewClientAttachment {

    /**
     * 附件类型，如 EXCALIDRAW、CODE。
     */
    private String type;

    /**
     * 附件格式，如 json、text。
     */
    private String format;

    /**
     * 可选语言，仅代码附件使用，如 java、typescript。
     */
    private String language;

    /**
     * 附件数据。绘图为 JSON 对象，代码为字符串。
     */
    private Object data;
}
