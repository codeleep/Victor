package me.codeleep.victor.common.context;

/**
 * 用户上下文工具类
 * 使用ThreadLocal存储当前请求的用户信息
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> API_KEY_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DEFAULT_INGEST_STATUS = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置当前用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 设置当前API Key ID
     */
    public static void setApiKeyId(Long apiKeyId) {
        API_KEY_ID.set(apiKeyId);
    }

    /**
     * 获取当前API Key ID
     */
    public static Long getApiKeyId() {
        return API_KEY_ID.get();
    }

    /**
     * 设置默认导入状态
     */
    public static void setDefaultIngestStatus(String defaultIngestStatus) {
        DEFAULT_INGEST_STATUS.set(defaultIngestStatus);
    }

    /**
     * 获取默认导入状态
     */
    public static String getDefaultIngestStatus() {
        return DEFAULT_INGEST_STATUS.get();
    }

    /**
     * 是否是API Key认证
     */
    public static boolean isApiKeyAuth() {
        return API_KEY_ID.get() != null;
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        API_KEY_ID.remove();
        DEFAULT_INGEST_STATUS.remove();
    }
}
