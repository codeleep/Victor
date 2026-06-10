package me.codeleep.victor.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.dto.MetadataQueryRequest;
import me.codeleep.victor.core.dto.MetadataRequest;
import me.codeleep.victor.core.dto.MetadataVO;
import me.codeleep.victor.core.service.MetadataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 元数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    /**
     * 根据分类获取元数据列表（前端下拉用）
     * @param category 分类（可选）
     */
    @GetMapping
    public Result<List<MetadataVO>> getByCategory(@RequestParam(required = false) String category) {
        if (category != null && !category.isEmpty()) {
            log.info("获取元数据: category={}", category);
            return Result.success(metadataService.getByCategory(category));
        } else {
            log.info("获取所有启用的元数据");
            return Result.success(metadataService.getByCategory(null));
        }
    }

    /**
     * 分页查询元数据列表（管理接口）
     */
    @GetMapping("/page")
    public Result<Page<MetadataVO>> page(MetadataQueryRequest request) {
        log.info("分页查询元数据: {}", request);
        return Result.success(metadataService.list(request));
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public Result<List<String>> getAllCategories() {
        log.info("获取所有元数据分类");
        return Result.success(metadataService.getAllCategories());
    }

    /**
     * 根据ID获取元数据
     */
    @GetMapping("/{id}")
    public Result<MetadataVO> getById(@PathVariable Long id) {
        log.info("获取元数据: id={}", id);
        return Result.success(metadataService.getDetail(id));
    }

    /**
     * 创建元数据
     */
    @PostMapping
    public Result<MetadataVO> create(@Valid @RequestBody MetadataRequest request) {
        log.info("创建元数据: category={}, code={}", request.getCategory(), request.getCode());
        return Result.success(metadataService.create(request));
    }

    /**
     * 更新元数据
     */
    @PutMapping("/{id}")
    public Result<MetadataVO> update(@PathVariable Long id, @Valid @RequestBody MetadataRequest request) {
        log.info("更新元数据: id={}, category={}, code={}", id, request.getCategory(), request.getCode());
        return Result.success(metadataService.update(id, request));
    }

    /**
     * 删除元数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除元数据: id={}", id);
        metadataService.delete(id);
        return Result.success();
    }
}
