package me.codeleep.victor.common.utils;

/**
 * 字符串工具类。
 */
public final class StrUtil {

    private StrUtil() {
    }

    /**
     * 查找最后一个完整句子的结束位置。
     *
     * <p>支持的标点：。！？!?；;</p>
     *
     * @param text 文本
     * @return 最后一个句子结束标点的索引，未找到返回 -1
     */
    public static int findLastSentenceEnd(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?'
                    || c == '；' || c == ';') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 按句子结束标点分割文本。
     *
     * <p>支持的标点：。！？!?；;</p>
     *
     * @param text 文本
     * @return 分割后的句子数组
     */
    public static String[] splitSentences(String text) {
        return text.split("(?<=[。！？!?；;])");
    }
}
