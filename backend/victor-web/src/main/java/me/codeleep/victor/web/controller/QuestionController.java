package me.codeleep.victor.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.QuestionQueryRequest;
import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.core.dto.QuestionVO;
import me.codeleep.victor.core.service.QuestionService;
import org.springframework.web.bind.annotation.*;

/**
 * 题目控制器
 */
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /**
     * 创建题目
     */
    @PostMapping
    public Result<QuestionVO> create(@Valid @RequestBody QuestionRequest request) {
        return Result.success(questionService.create(request));
    }

    /**
     * 根据ID获取题目
     */
    @GetMapping("/{id}")
    public Result<QuestionVO> getById(@PathVariable Long id) {
        return Result.success(questionService.getById(id));
    }

    /**
     * 更新题目
     */
    @PutMapping("/{id}")
    public Result<QuestionVO> update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return Result.success(questionService.update(id, request));
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询题目列表
     */
    @GetMapping
    public Result<Page<QuestionVO>> list(QuestionQueryRequest request) {
        return Result.success(questionService.list(request));
    }

    /**
     * 审核通过题目
     */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        questionService.approve(id);
        return Result.success();
    }

    /**
     * 审核拒绝题目
     */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        questionService.reject(id);
        return Result.success();
    }
}