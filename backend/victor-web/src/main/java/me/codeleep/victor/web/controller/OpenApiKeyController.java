package me.codeleep.victor.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.OpenApiKeyRequest;
import me.codeleep.victor.core.dto.OpenApiKeyVO;
import me.codeleep.victor.core.service.OpenApiKeyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Key管理控制器
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class OpenApiKeyController {

    private final OpenApiKeyService openApiKeyService;

    /**
     * 创建API Key
     */
    @PostMapping
    public Result<OpenApiKeyVO> create(@Valid @RequestBody OpenApiKeyRequest request) {
        return Result.success(openApiKeyService.create(request));
    }

    /**
     * 根据ID获取API Key
     */
    @GetMapping("/{id}")
    public Result<OpenApiKeyVO> getById(@PathVariable Long id) {
        return Result.success(openApiKeyService.getById(id));
    }

    /**
     * 更新API Key
     */
    @PutMapping("/{id}")
    public Result<OpenApiKeyVO> update(@PathVariable Long id, @Valid @RequestBody OpenApiKeyRequest request) {
        return Result.success(openApiKeyService.update(id, request));
    }

    /**
     * 删除API Key
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        openApiKeyService.delete(id);
        return Result.success();
    }

    /**
     * 获取当前用户的API Key列表
     */
    @GetMapping
    public Result<List<OpenApiKeyVO>> list() {
        return Result.success(openApiKeyService.listByCurrentUser());
    }

    /**
     * 验证API Key是否有效
     */
    @GetMapping("/validate")
    public Result<Boolean> validate(@RequestParam String apiKey) {
        return Result.success(openApiKeyService.validate(apiKey));
    }
}
