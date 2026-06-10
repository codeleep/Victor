package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.*;
import me.codeleep.victor.core.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户视图对象(含token)
     */
    UserVO register(UserRegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @param ipAddress 客户端IP地址
     * @return 用户视图对象(含token)
     */
    UserVO login(UserLoginRequest request, String ipAddress);

    /**
     * 根据ID获取用户
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getUserById(Long userId);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User getUserByUsername(String username);

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param request 更新请求
     * @return 用户视图对象
     */
    UserVO updateUser(Long userId, UserUpdateRequest request);

    /**
     * 修改密码
     *
     * @param userId 用户ID
     * @param request 密码修改请求
     */
    void changePassword(Long userId, PasswordChangeRequest request);
}
