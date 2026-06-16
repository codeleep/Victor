package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.AgentLlmConfigRequest;
import me.codeleep.victor.core.dto.AgentLlmConfigVO;
import me.codeleep.victor.core.service.AgentLlmConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AgentLlmConfigController {

    private final AgentLlmConfigService agentLlmConfigService;

    /**
     * 创建LLM配置
     */
    @PostMapping({"/llm-configs", "/agents/{agentId}/llm-config"})
    public Result<AgentLlmConfigVO> create(@Valid @RequestBody AgentLlmConfigRequest request) {
        log.info("创建LLM配置请求: name={}", request.getName());
        AgentLlmConfigVO vo = agentLlmConfigService.create(request);
        return Result.success(vo);
    }

    /**
     * 更新LLM配置
     */
    @PutMapping({"/llm-configs/{id}", "/agents/{agentId}/llm-config/{id}"})
    public Result<AgentLlmConfigVO> update(@PathVariable Long id,
                                            @Valid @RequestBody AgentLlmConfigRequest request) {
        log.info("更新LLM配置请求: id={}", id);
        AgentLlmConfigVO vo = agentLlmConfigService.update(id, request);
        return Result.success(vo);
    }

    /**
     * 删除LLM配置
     */
    @DeleteMapping({"/llm-configs/{id}", "/agents/{agentId}/llm-config/{id}"})
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除LLM配置请求: id={}", id);
        agentLlmConfigService.delete(id);
        return Result.success();
    }

    /**
     * 获取LLM配置详情
     */
    @GetMapping({"/llm-configs/{id}", "/agents/{agentId}/llm-config/{id}"})
    public Result<AgentLlmConfigVO> getById(@PathVariable Long id) {
        AgentLlmConfigVO vo = agentLlmConfigService.getVOById(id);
        return Result.success(vo);
    }

    /**
     * 获取当前用户的LLM配置列表
     */
    @GetMapping({"/llm-configs", "/agents/{agentId}/llm-config"})
    public Result<List<AgentLlmConfigVO>> list() {
        List<AgentLlmConfigVO> vos = agentLlmConfigService.listByCurrentUser();
        return Result.success(vos);
    }

    /**
     * 设置默认配置
     */
    @PutMapping({"/llm-configs/{id}/default", "/agents/{agentId}/llm-config/{id}/default"})
    public Result<Void> setDefault(@PathVariable Long id) {
        agentLlmConfigService.setDefault(id);
        return Result.success();
    }

    /**
     * 测试LLM配置连接
     */
    @PostMapping({"/llm-configs/{id}/test", "/agents/{agentId}/llm-config/{id}/test"})
    public Result<Void> testConnection(@PathVariable Long id) {
        log.info("测试LLM配置连接请求: id={}", id);
        agentLlmConfigService.testConnection(id);
        return Result.success();
    }
}

