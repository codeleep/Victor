package me.codeleep.victor.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.SystemInitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemInitController {

    private final SystemInitService systemInitService;

    /**
     * 检查当前用户是否已完成系统初始化
     */
    @GetMapping("/init/status")
    public Result<Boolean> initStatus() {
        return Result.success(systemInitService.isInitialized());
    }

    /**
     * 一键初始化系统 Agent、Agent Team 和默认 LLM 配置
     * 幂等操作：已初始化的用户会直接跳过
     */
    @PostMapping("/init")
    public Result<Map<String, Object>> init() {
        log.info("系统初始化请求");
        Map<String, Object> result = systemInitService.initSystemAgents();
        return Result.success(result);
    }
}
