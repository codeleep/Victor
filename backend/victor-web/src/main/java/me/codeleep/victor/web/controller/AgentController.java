package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.core.dto.AgentVO;
import me.codeleep.victor.core.dto.ToolVO;
import me.codeleep.victor.core.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 创建Agent
     */
    @PostMapping
    public Result<AgentVO> create(@Valid @RequestBody AgentRequest request) {
        log.info("创建Agent请求: name={}", request.getName());
        AgentVO vo = agentService.create(request);
        return Result.success(vo);
    }

    /**
     * 更新Agent
     */
    @PutMapping("/{id}")
    public Result<AgentVO> update(@PathVariable Long id,
                                  @Valid @RequestBody AgentRequest request) {
        log.info("更新Agent请求: id={}", id);
        AgentVO vo = agentService.update(id, request);
        return Result.success(vo);
    }

    /**
     * 删除Agent
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除Agent请求: id={}", id);
        agentService.delete(id);
        return Result.success();
    }

    /**
     * 获取Agent详情
     */
    @GetMapping("/{id}")
    public Result<AgentVO> getById(@PathVariable Long id) {
        AgentVO vo = agentService.getVOById(id);
        return Result.success(vo);
    }

    /**
     * 获取当前用户的Agent列表
     */
    @GetMapping
    public Result<List<AgentVO>> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            List<AgentVO> vos = agentService.listByType(type);
            return Result.success(vos);
        }
        List<AgentVO> vos = agentService.listByCurrentUser();
        return Result.success(vos);
    }

    /**
     * 获取可用工具列表
     */
    @GetMapping("/tools")
    public Result<List<ToolVO>> listAvailableTools() {
        List<ToolVO> tools = agentService.listAvailableTools();
        return Result.success(tools);
    }
}
