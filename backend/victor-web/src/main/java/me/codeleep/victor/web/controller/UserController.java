package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.PasswordChangeRequest;
import me.codeleep.victor.core.dto.UserUpdateRequest;
import me.codeleep.victor.core.dto.UserVO;
import me.codeleep.victor.core.entity.User;
import me.codeleep.victor.core.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = UserContext.getUserId();
        log.info("获取当前用户信息: userId={}", userId);
        User user = userService.getUserById(userId);
        return Result.success(convertToVO(user));
    }

    /**
     * 更新当前用户信息
     */
    @PutMapping("/me")
    public Result<UserVO> updateCurrentUser(@Valid @RequestBody UserUpdateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("更新当前用户信息: userId={}", userId);
        UserVO userVO = userService.updateUser(userId, request);
        return Result.success(userVO);
    }

    /**
     * 修改密码
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        Long userId = UserContext.getUserId();
        log.info("修改密码: userId={}", userId);
        userService.changePassword(userId, request);
        return Result.success();
    }

    /**
     * 转换为VO（不含token）
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
