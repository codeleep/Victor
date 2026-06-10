package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.dto.MetadataQueryRequest;
import me.codeleep.victor.core.dto.MetadataRequest;
import me.codeleep.victor.core.dto.MetadataVO;
import me.codeleep.victor.core.entity.Metadata;
import me.codeleep.victor.core.mapper.MetadataMapper;
import me.codeleep.victor.core.service.MetadataService;
import me.codeleep.victor.core.service.converter.MetadataConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataServiceImpl extends ServiceImpl<MetadataMapper, Metadata> implements MetadataService {

    private final ObjectMapper objectMapper;
    private final MetadataConverter metadataConverter;

    @Override
    public List<MetadataVO> getByCategory(String category) {
        LambdaQueryWrapper<Metadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Metadata::getIsActive, true);
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Metadata::getCategory, category);
        }
        wrapper.orderByAsc(Metadata::getSortOrder);

        List<Metadata> list = list(wrapper);
        return list.stream().map(metadataConverter::toVO).collect(Collectors.toList());
    }

    @Override
    public List<String> getAllCategories() {
        LambdaQueryWrapper<Metadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Metadata::getIsActive, true)
              .select(Metadata::getCategory)
              .groupBy(Metadata::getCategory);

        List<Metadata> list = list(wrapper);
        return list.stream().map(Metadata::getCategory).distinct().collect(Collectors.toList());
    }

    @Override
    public Page<MetadataVO> list(MetadataQueryRequest request) {
        Page<Metadata> page = new Page<>(request.getPage() + 1, request.getSize());

        LambdaQueryWrapper<Metadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCategory()), Metadata::getCategory, request.getCategory())
               .like(StringUtils.hasText(request.getCode()), Metadata::getCode, request.getCode())
               .like(StringUtils.hasText(request.getName()), Metadata::getName, request.getName())
               .eq(request.getIsActive() != null, Metadata::getIsActive, request.getIsActive())
               .orderByAsc(Metadata::getCategory)
               .orderByAsc(Metadata::getSortOrder);

        Page<Metadata> result = page(page, wrapper);

        Page<MetadataVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(metadataConverter::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public MetadataVO create(MetadataRequest request) {
        // 检查是否已存在
        LambdaQueryWrapper<Metadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Metadata::getCategory, request.getCategory())
               .eq(Metadata::getCode, request.getCode());
        if (getOne(wrapper) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该分类下编码已存在: " + request.getCode());
        }

        Metadata metadata = new Metadata();
        metadata.setCategory(request.getCategory());
        metadata.setCode(request.getCode());
        metadata.setName(request.getName());
        metadata.setDescription(request.getDescription());
        metadata.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        metadata.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        // 序列化extraData
        if (request.getExtraData() != null) {
            try {
                metadata.setExtraData(objectMapper.writeValueAsString(request.getExtraData()));
            } catch (Exception e) {
                log.warn("序列化extraData失败", e);
            }
        }

        save(metadata);
        return metadataConverter.toVO(metadata);
    }

    @Override
    public MetadataVO update(Long id, MetadataRequest request) {
        Metadata metadata = getById(id);
        if (metadata == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "元数据不存在: " + id);
        }

        // 如果修改了category或code，检查是否冲突
        if (!metadata.getCategory().equals(request.getCategory()) ||
            !metadata.getCode().equals(request.getCode())) {
            LambdaQueryWrapper<Metadata> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Metadata::getCategory, request.getCategory())
                   .eq(Metadata::getCode, request.getCode())
                   .ne(Metadata::getId, id);
            if (getOne(wrapper) != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "该分类下编码已存在: " + request.getCode());
            }
        }

        metadata.setCategory(request.getCategory());
        metadata.setCode(request.getCode());
        metadata.setName(request.getName());
        metadata.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            metadata.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            metadata.setIsActive(request.getIsActive());
        }

        // 序列化extraData
        if (request.getExtraData() != null) {
            try {
                metadata.setExtraData(objectMapper.writeValueAsString(request.getExtraData()));
            } catch (Exception e) {
                log.warn("序列化extraData失败", e);
            }
        }

        updateById(metadata);
        return metadataConverter.toVO(metadata);
    }

    @Override
    public void delete(Long id) {
        Metadata metadata = getById(id);
        if (metadata == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "元数据不存在: " + id);
        }
        removeById(id);
    }

    @Override
    public MetadataVO getDetail(Long id) {
        Metadata metadata = getById(id);
        if (metadata == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "元数据不存在: " + id);
        }
        return metadataConverter.toVO(metadata);
    }

}
