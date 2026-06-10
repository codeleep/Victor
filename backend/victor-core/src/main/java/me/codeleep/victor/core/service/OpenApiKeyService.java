package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.OpenApiKeyRequest;
import me.codeleep.victor.core.dto.OpenApiKeyVO;

import java.util.List;

/**
 * Open API Key服务接口
 */
public interface OpenApiKeyService {

    /**
     * 创建API Key
     */
    OpenApiKeyVO create(OpenApiKeyRequest request);

    /**
     * 更新API Key
     */
    OpenApiKeyVO update(Long id, OpenApiKeyRequest request);

    /**
     * 删除API Key
     */
    void delete(Long id);

    /**
     * 根据ID获取API Key
     */
    OpenApiKeyVO getById(Long id);

    /**
     * 获取当前用户的所有API Key
     */
    List<OpenApiKeyVO> listByCurrentUser();

    /**
     * 根据Key值获取API Key
     */
    OpenApiKeyVO getByKey(String apiKey);

    /**
     * 验证API Key是否有效
     */
    boolean validate(String apiKey);
}
