package me.codeleep.victor.core.dto;

import lombok.Data;

/**
 * 用户更新请求
 */
@Data
public class UserUpdateRequest {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;
}
