package me.codeleep.victor.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.JobQueryRequest;
import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.core.dto.JobVO;
import me.codeleep.victor.core.service.JobService;
import org.springframework.web.bind.annotation.*;

/**
 * 岗位控制器
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * 创建岗位
     */
    @PostMapping
    public Result<JobVO> create(@Valid @RequestBody JobRequest request) {
        return Result.success(jobService.create(request));
    }

    /**
     * 根据ID获取岗位
     */
    @GetMapping("/{id}")
    public Result<JobVO> getById(@PathVariable Long id) {
        return Result.success(jobService.getById(id));
    }

    /**
     * 更新岗位
     */
    @PutMapping("/{id}")
    public Result<JobVO> update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return Result.success(jobService.update(id, request));
    }

    /**
     * 删除岗位
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询岗位列表
     */
    @GetMapping
    public Result<Page<JobVO>> list(JobQueryRequest request) {
        return Result.success(jobService.list(request));
    }

    /**
     * 审核通过岗位
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        jobService.approve(id);
        return Result.success();
    }

    /**
     * 审核拒绝岗位
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        jobService.reject(id);
        return Result.success();
    }
}