package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;
import me.codeleep.victor.core.service.interview.InterviewQuestionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 面试出题控制器(出题线)。
 * 负责面试配置管理与异步出题触发, 路径保持 /interview-configs 不变。
 */
@RestController
@RequestMapping("/api/v1/interview-configs")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionService interviewQuestionService;

    /** 创建面试配置 */
    @PostMapping
    public Result<Long> createConfig(@Valid @RequestBody InterviewConfigRequest request) {
        Long id = interviewQuestionService.createConfig(request);
        return Result.success(id);
    }

    /** 获取面试配置列表 */
    @GetMapping
    public Result<List<InterviewConfigVO>> listConfigs() {
        List<InterviewConfigVO> configs = interviewQuestionService.listConfigs();
        return Result.success(configs);
    }

    /** 召回资料预览 */
    @PostMapping("/recall-preview")
    public Result<List<Map<String, Object>>> previewRecallItems(@RequestBody InterviewConfigRequest request) {
        List<Map<String, Object>> items = interviewQuestionService.previewRecallItems(request);
        return Result.success(items);
    }

    /** 获取面试配置详情 */
    @GetMapping("/{id}")
    public Result<InterviewConfigVO> getConfig(@PathVariable Long id) {
        InterviewConfigVO config = interviewQuestionService.getConfig(id);
        return Result.success(config);
    }

    /** 更新面试配置 */
    @PutMapping("/{id}")
    public Result<Void> updateConfig(@PathVariable Long id, @Valid @RequestBody InterviewConfigRequest request) {
        interviewQuestionService.updateConfig(id, request);
        return Result.success();
    }

    /** 删除面试配置 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        interviewQuestionService.deleteConfig(id);
        return Result.success();
    }

    /** 发布配置, 触发异步出题 */
    @PostMapping("/{id}/publish")
    public Result<Void> publishConfig(@PathVariable Long id) {
        interviewQuestionService.publishConfig(id);
        return Result.success();
    }

    /** 归档配置 */
    @PostMapping("/{id}/archive")
    public Result<Void> archiveConfig(@PathVariable Long id) {
        interviewQuestionService.archiveConfig(id);
        return Result.success();
    }
}