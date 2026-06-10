package me.codeleep.victor.core.dto;

import lombok.Data;
import me.codeleep.victor.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 */
@Data
public class UserVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 状态
     */
    private UserStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * JWT Token (登录/注册响应时返回)
     */
    private String token;
}
