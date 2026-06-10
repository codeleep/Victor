package me.codeleep.victor.common.utils;

import java.util.function.Supplier;

/**
 * 安全取值工具
 * 用于安全地获取值，发生异常时返回默认值
 */
public class SafeGet {

    /**
     * 安全获取值，异常时返回 null
     *
     * @param supplier 取值逻辑
     * @return 正常返回值，异常返回 null
     */
    public static <T> T get(Supplier<T> supplier) {
        return get(supplier, null);
    }

    /**
     * 安全获取值，异常时返回默认值
     *
     * @param supplier     取值逻辑
     * @param defaultValue 默认值
     * @return 正常返回值，异常返回默认值
     */
    public static <T> T get(Supplier<T> supplier, T defaultValue) {
        try {
            T result = supplier.get();
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
