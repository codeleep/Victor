package me.codeleep.victor.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.constant.Constants;
import me.codeleep.victor.common.enums.UserStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.dto.PasswordChangeRequest;
import me.codeleep.victor.core.dto.UserLoginRequest;
import me.codeleep.victor.core.dto.UserRegisterRequest;
import me.codeleep.victor.core.dto.UserUpdateRequest;
import me.codeleep.victor.core.dto.UserVO;
import me.codeleep.victor.core.entity.LoginFailureLog;
import me.codeleep.victor.core.entity.User;
import me.codeleep.victor.core.mapper.LoginFailureLogMapper;
import me.codeleep.victor.core.mapper.UserMapper;
import me.codeleep.victor.core.service.UserService;
import me.codeleep.victor.core.service.converter.UserConverter;
import me.codeleep.victor.web.config.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final LoginFailureLogMapper loginFailureLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserConverter userConverter;

    @Override
    @Transactional
    public UserVO register(UserRegisterRequest request) {
        log.info("用户注册: username={}, email={}", request.getUsername(), request.getEmail());

        // 检查用户名是否已存在
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setStatus(UserStatus.ACTIVE);

        userMapper.insert(user);
        log.info("用户注册成功: userId={}", user.getId());

        // 生成token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());

        return toVOWithToken(user, token);
    }

    @Override
    @Transactional
    public UserVO login(UserLoginRequest request, String ipAddress) {
        log.info("用户登录: username={}, ip={}", request.getUsername(), ipAddress);

        String username = request.getUsername();

        // 检查是否被锁定（查询最近锁定时间窗口内的失败次数）
        LocalDateTime lockWindowStart = LocalDateTime.now().minusMinutes(Constants.LOGIN_LOCK_MINUTES);
        Long recentFailureCount = loginFailureLogMapper.selectCount(
                new LambdaQueryWrapper<LoginFailureLog>()
                        .eq(LoginFailureLog::getUsername, username)
                        .ge(LoginFailureLog::getAttemptedAt, lockWindowStart)
        );
        if (recentFailureCount >= Constants.LOGIN_FAILURE_LIMIT) {
            log.warn("用户登录失败，账户已被锁定: username={}", username);
            throw new BusinessException(ResultCode.LOGIN_FAILURE_LIMIT_EXCEEDED);
        }

        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );

        if (user == null) {
            log.warn("用户登录失败，用户不存在: username={}", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查用户状态
        if (user.getStatus() == UserStatus.LOCKED) {
            log.warn("用户登录失败，账户状态异常: username={}, status={}", username, user.getStatus());
            throw new BusinessException(ResultCode.USER_LOCKED);
        }

        if (user.getStatus() == UserStatus.DELETED) {
            log.warn("用户登录失败，账户已删除: username={}", username);
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("用户登录失败，密码错误: username={}", username);
            handleLoginFailure(username, ipAddress);
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }

        // 登录成功，清除失败记录
        clearLoginFailureRecords(username);

        // 生成token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功: userId={}, username={}", user.getId(), username);

        return toVOWithToken(user, token);
    }

    @Override
    public User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public User getUserByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    @Transactional
    public UserVO updateUser(Long userId, UserUpdateRequest request) {
        log.info("更新用户信息: userId={}", userId);

        User user = getUserById(userId);

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }

        userMapper.updateById(user);
        log.info("用户信息更新成功: userId={}", userId);

        return userConverter.toVO(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        log.info("修改密码: userId={}", userId);

        User user = getUserById(userId);

        // 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_INCORRECT);
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        log.info("密码修改成功: userId={}", userId);
    }

    /**
     * 处理登录失败
     */
    private void handleLoginFailure(String username, String ipAddress) {
        // 记录失败日志
        LoginFailureLog failureLog = new LoginFailureLog();
        failureLog.setUsername(username);
        failureLog.setIpAddress(ipAddress);
        failureLog.setAttemptedAt(LocalDateTime.now());
        loginFailureLogMapper.insert(failureLog);
    }

    /**
     * 清除登录失败记录
     */
    private void clearLoginFailureRecords(String username) {
        loginFailureLogMapper.delete(
                new LambdaQueryWrapper<LoginFailureLog>().eq(LoginFailureLog::getUsername, username)
        );
    }

    private UserVO toVOWithToken(User user, String token) {
        UserVO vo = userConverter.toVO(user);
        vo.setToken(token);
        return vo;
    }
}
