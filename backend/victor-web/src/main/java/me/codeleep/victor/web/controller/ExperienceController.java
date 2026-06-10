package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.enums.ExperienceType;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.ExperienceRequest;
import me.codeleep.victor.core.dto.ExperienceVO;
import me.codeleep.victor.core.service.ExperienceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 经历控制器
 */
@RestController
@RequestMapping("/api/v1/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    /**
     * 创建经历
     */
    @PostMapping
    public Result<ExperienceVO> create(@Valid @RequestBody ExperienceRequest request) {
        return Result.success(experienceService.create(request));
    }

    /**
     * 根据ID获取经历
     */
    @GetMapping("/{id}")
    public Result<ExperienceVO> getById(@PathVariable Long id) {
        return Result.success(experienceService.getById(id));
    }

    /**
     * 更新经历
     */
    @PutMapping("/{id}")
    public Result<ExperienceVO> update(@PathVariable Long id, @Valid @RequestBody ExperienceRequest request) {
        return Result.success(experienceService.update(id, request));
    }

    /**
     * 删除经历
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        experienceService.delete(id);
        return Result.success();
    }

    /**
     * 获取当前用户的经历列表
     */
    @GetMapping
    public Result<List<ExperienceVO>> list(@RequestParam(required = false) ExperienceType type) {
        if (type != null) {
            return Result.success(experienceService.listByUserIdAndType(type));
        }
        return Result.success(experienceService.listByUserId());
    }

    /**
     * 审核通过经历
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        experienceService.approve(id);
        return Result.success();
    }

    /**
     * 审核拒绝经历
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        experienceService.reject(id);
        return Result.success();
    }
}