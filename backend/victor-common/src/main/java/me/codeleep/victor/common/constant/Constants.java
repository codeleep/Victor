package me.codeleep.victor.common.constant;

/**
 * 系统常量
 */
public interface Constants {

    /**
     * 默认页码
     */
    int DEFAULT_PAGE = 1;

    /**
     * 默认每页大小
     */
    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页大小
     */
    int MAX_PAGE_SIZE = 100;

    /**
     * Token前缀
     */
    String TOKEN_PREFIX = "Bearer ";

    /**
     * Token请求头
     */
    String TOKEN_HEADER = "Authorization";

    /**
     * 用户ID请求属性
     */
    String USER_ID_ATTR = "userId";

    /**
     * 登录失败次数限制
     */
    int LOGIN_FAILURE_LIMIT = 5;

    /**
     * 登录锁定时间（分钟）
     */
    int LOGIN_LOCK_MINUTES = 15;

    /**
     * Token有效期（天）
     */
    int TOKEN_EXPIRE_DAYS = 7;

    /**
     * 向量维度
     */
    int VECTOR_DIMENSION = 1536;

    /**
     * 默认分块大小
     */
    int DEFAULT_CHUNK_SIZE = 500;

    /**
     * 默认分块重叠
     */
    int DEFAULT_CHUNK_OVERLAP = 50;

    /**
     * 默认召回数量
     */
    int DEFAULT_RECALL_COUNT = 50;

    /**
     * 最大召回数量
     */
    int MAX_RECALL_COUNT = 200;

    /**
     * 文件存储路径
     */
    String FILE_STORAGE_PATH = "storage/files";

    /**
     * 语音文件路径
     */
    String AUDIO_STORAGE_PATH = "storage/audio";

    /**
     * 插件安装路径
     */
    String PLUGIN_INSTALL_PATH = "storage/plugins";
}
