package me.codeleep.victor.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.UserLoginRequest;
import me.codeleep.victor.core.dto.UserRegisterRequest;
import me.codeleep.victor.core.dto.UserVO;
import me.codeleep.victor.core.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRegisterRequest request) {
        log.info("注册请求: username={}", request.getUsername());
        UserVO userVO = userService.register(request);
        return Result.success(userVO);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        log.info("登录请求: username={}", request.getUsername());
        String ipAddress = getClientIpAddress(httpRequest);
        UserVO userVO = userService.login(request, ipAddress);
        return Result.success(userVO);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
