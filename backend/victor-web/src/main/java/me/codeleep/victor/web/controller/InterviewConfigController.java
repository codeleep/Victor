package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;
import me.codeleep.victor.core.service.interview.InterviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 面试配置控制器
 */
@RestController
@RequestMapping("/api/v1/interview-configs")
@RequiredArgsConstructor
public class InterviewConfigController {

    private final InterviewService interviewService;

    /**
     * 创建面试配置
     */
    @PostMapping
    public Result<Long> createConfig(@Valid @RequestBody InterviewConfigRequest request) {
        Long id = interviewService.createConfig(request);
        return Result.success(id);
    }

    /**
     * 获取面试配置列表
     */
    @GetMapping
    public Result<List<InterviewConfigVO>> listConfigs() {
        List<InterviewConfigVO> configs = interviewService.listConfigs();
        return Result.success(configs);
    }

    @PostMapping("/recall-preview")
    public Result<List<Map<String, Object>>> previewRecallItems(@RequestBody InterviewConfigRequest request) {
        List<Map<String, Object>> items = interviewService.previewRecallItems(request);
        return Result.success(items);
    }

    /**
     * 获取面试配置详情
     */
    @GetMapping("/{id}")
    public Result<InterviewConfigVO> getConfig(@PathVariable Long id) {
        InterviewConfigVO config = interviewService.getConfig(id);
        return Result.success(config);
    }

    /**
     * 更新面试配置
     */
    @PutMapping("/{id}")
    public Result<Void> updateConfig(@PathVariable Long id, @Valid @RequestBody InterviewConfigRequest request) {
        interviewService.updateConfig(id, request);
        return Result.success();
    }

    /**
     * 删除面试配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        interviewService.deleteConfig(id);
        return Result.success();
    }

    /**
     * 发布面试配置
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publishConfig(@PathVariable Long id) {
        interviewService.publishConfig(id);
        return Result.success();
    }

    /**
     * 归档面试配置
     */
    @PostMapping("/{id}/archive")
    public Result<Void> archiveConfig(@PathVariable Long id) {
        interviewService.archiveConfig(id);
        return Result.success();
    }
}
