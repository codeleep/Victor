package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.enums.OpenApiKeyStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.dto.OpenApiKeyRequest;
import me.codeleep.victor.core.dto.OpenApiKeyVO;
import me.codeleep.victor.core.entity.OpenApiKey;
import me.codeleep.victor.core.mapper.OpenApiKeyMapper;
import me.codeleep.victor.core.service.OpenApiKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Open API Key服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiKeyServiceImpl implements OpenApiKeyService {

    private final OpenApiKeyMapper openApiKeyMapper;

    @Override
    @Transactional
    public OpenApiKeyVO create(OpenApiKeyRequest request) {
        log.info("创建Open API Key: name={}", request.getName());

        OpenApiKey apiKey = new OpenApiKey();
        apiKey.setUserId(UserContext.getUserId());
        apiKey.setName(request.getName());
        apiKey.setDescription(request.getDescription());
        apiKey.setApiKey(request.getApiKey());
        apiKey.setScopes(request.getScopes());
        apiKey.setDefaultIngestStatus(request.getDefaultIngestStatus());
        apiKey.setStatus(request.getStatus() != null ? request.getStatus() : OpenApiKeyStatus.ENABLED);
        apiKey.setExpiresAt(request.getExpiresAt());

        openApiKeyMapper.insert(apiKey);
        log.info("Open API Key创建成功: id={}", apiKey.getId());

        return toVO(apiKey);
    }

    @Override
    @Transactional
    public OpenApiKeyVO update(Long id, OpenApiKeyRequest request) {
        log.info("更新Open API Key: id={}", id);

        OpenApiKey existing = getEntityById(id);
        checkOwnership(existing);

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setApiKey(request.getApiKey());
        existing.setScopes(request.getScopes());
        existing.setDefaultIngestStatus(request.getDefaultIngestStatus());
        existing.setStatus(request.getStatus());
        existing.setExpiresAt(request.getExpiresAt());

        openApiKeyMapper.updateById(existing);
        log.info("Open API Key更新成功: id={}", id);

        return toVO(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除Open API Key: id={}", id);

        OpenApiKey apiKey = getEntityById(id);
        checkOwnership(apiKey);

        openApiKeyMapper.deleteById(id);
        log.info("Open API Key删除成功: id={}", id);
    }

    @Override
    public OpenApiKeyVO getById(Long id) {
        return toVO(getEntityById(id));
    }

    @Override
    public List<OpenApiKeyVO> listByCurrentUser() {
        Long userId = UserContext.getUserId();
        List<OpenApiKey> list = openApiKeyMapper.selectList(
                new LambdaQueryWrapper<OpenApiKey>()
                        .eq(OpenApiKey::getUserId, userId)
                        .orderByDesc(OpenApiKey::getCreatedAt)
        );
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public OpenApiKeyVO getByKey(String apiKey) {
        Long userId = UserContext.getUserId();
        OpenApiKey key = openApiKeyMapper.selectOne(
                new LambdaQueryWrapper<OpenApiKey>()
                        .eq(OpenApiKey::getUserId, userId)
                        .eq(OpenApiKey::getApiKey, apiKey)
        );
        return key != null ? toVO(key) : null;
    }

    @Override
    public boolean validate(String apiKey) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }

        OpenApiKey key = openApiKeyMapper.selectOne(
                new LambdaQueryWrapper<OpenApiKey>()
                        .eq(OpenApiKey::getUserId, userId)
                        .eq(OpenApiKey::getApiKey, apiKey)
        );
        if (key == null) {
            return false;
        }

        if (key.getStatus() != OpenApiKeyStatus.ENABLED) {
            return false;
        }

        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        // 更新最后使用时间
        key.setLastUsedAt(LocalDateTime.now());
        openApiKeyMapper.updateById(key);

        return true;
    }

    private OpenApiKey getEntityById(Long id) {
        OpenApiKey apiKey = openApiKeyMapper.selectById(id);
        if (apiKey == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Open API Key不存在");
        }
        return apiKey;
    }

    private void checkOwnership(OpenApiKey apiKey) {
        if (!apiKey.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该Open API Key");
        }
    }

    private OpenApiKeyVO toVO(OpenApiKey entity) {
        OpenApiKeyVO vo = new OpenApiKeyVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setApiKey(entity.getApiKey());
        vo.setScopes(entity.getScopes());
        vo.setDefaultIngestStatus(entity.getDefaultIngestStatus());
        vo.setStatus(entity.getStatus());
        vo.setExpiresAt(entity.getExpiresAt());
        vo.setLastUsedAt(entity.getLastUsedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
