package me.codeleep.victor.web.websocket.config;

/**
 * 认证提供者接口。
 *
 * <p>支持不同的认证方式扩展。每种认证方式可以有自己的参数配置。</p>
 *
 * <h3>扩展方式：</h3>
 * <ol>
 *   <li>实现此接口</li>
 *   <li>在 application.yml 中配置相应的参数</li>
 *   <li>注册为 Spring Bean</li>
 * </ol>
 */
public interface AuthProvider {

    /**
     * 获取认证类型标识。
     *
     * @return 认证类型，如 "apikey"、"token"、"oauth" 等
     */
    String getType();

    /**
     * 获取用于连接ASR服务的认证头信息。
     *
     * @return 认证头信息Map，如 {"X-Api-Key": "xxx"}
     */
    java.util.Map<String, String> getAsrAuthHeaders();

    /**
     * 获取用于连接TTS服务的认证头信息。
     *
     * @return 认证头信息Map
     */
    java.util.Map<String, String> getTtsAuthHeaders();

    /**
     * 检查认证是否有效。
     *
     * @return 有效返回true
     */
    boolean isValid();
}