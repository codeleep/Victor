package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.ResumeUpdateRequest;
import me.codeleep.victor.core.dto.ResumeUploadRequest;
import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.service.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历控制器
 */
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 上传简历
     */
    @PostMapping("/upload")
    public Result<ResumeVO> upload(@Valid ResumeUploadRequest request,
                                   @RequestParam("file") MultipartFile file) {
        return Result.success(resumeService.upload(UserContext.getUserId(), request.getName(), file));
    }

    /**
     * 解析简历
     */
    @PostMapping("/{id}/parse")
    public Result<Void> parse(@PathVariable Long id) {
        resumeService.parse(id);
        return Result.success();
    }

    /**
     * 根据ID获取简历
     */
    @GetMapping("/{id}")
    public Result<ResumeVO> getById(@PathVariable Long id) {
        return Result.success(resumeService.getById(id));
    }

    /**
     * 获取当前用户的简历列表
     */
    @GetMapping
    public Result<List<ResumeVO>> list() {
        return Result.success(resumeService.listByUserId());
    }

    /**
     * 删除简历
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success();
    }

    /**
     * 更新简历解析文本
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ResumeUpdateRequest request) {
        resumeService.updateRawText(id, request.getRawText());
        return Result.success();
    }

    /**
     * 审核通过简历
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        resumeService.approve(id);
        return Result.success();
    }

    /**
     * 审核拒绝简历
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        resumeService.reject(id);
        return Result.success();
    }
}
