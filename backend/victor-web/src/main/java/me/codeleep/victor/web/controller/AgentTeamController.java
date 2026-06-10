package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.AgentTeamVO;
import me.codeleep.victor.core.dto.TeamRequest;
import me.codeleep.victor.core.service.AgentTeamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent团队控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent-teams")
@RequiredArgsConstructor
public class AgentTeamController {

    private final AgentTeamService agentTeamService;

    /**
     * 创建团队
     */
    @PostMapping
    public Result<AgentTeamVO> create(@Valid @RequestBody TeamRequest request) {
        log.info("创建Agent团队请求: name={}", request.getName());
        AgentTeamVO vo = agentTeamService.create(request);
        return Result.success(vo);
    }

    /**
     * 更新团队
     */
    @PutMapping("/{id}")
    public Result<AgentTeamVO> update(@PathVariable Long id,
                                      @Valid @RequestBody TeamRequest request) {
        log.info("更新Agent团队请求: id={}", id);
        AgentTeamVO vo = agentTeamService.update(id, request);
        return Result.success(vo);
    }

    /**
     * 删除团队
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除Agent团队请求: id={}", id);
        agentTeamService.delete(id);
        return Result.success();
    }

    /**
     * 获取团队详情
     */
    @GetMapping("/{id}")
    public Result<AgentTeamVO> getById(@PathVariable Long id) {
        AgentTeamVO vo = agentTeamService.getVOById(id);
        return Result.success(vo);
    }

    /**
     * 获取当前用户的团队列表
     */
    @GetMapping
    public Result<List<AgentTeamVO>> list() {
        List<AgentTeamVO> vos = agentTeamService.listByCurrentUser();
        return Result.success(vos);
    }
}
