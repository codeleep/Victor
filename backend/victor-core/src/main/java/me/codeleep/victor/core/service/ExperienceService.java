package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.ExperienceRequest;
import me.codeleep.victor.core.dto.ExperienceVO;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.common.enums.ExperienceType;

import java.util.List;

/**
 * 经历服务接口
 */
public interface ExperienceService {

    /**
     * 创建经历
     */
    ExperienceVO create(ExperienceRequest request);

    /**
     * 根据ID获取经历
     */
    ExperienceVO getById(Long id);

    /**
     * 更新经历
     */
    ExperienceVO update(Long id, ExperienceRequest request);

    /**
     * 删除经历
     */
    void delete(Long id);

    /**
     * 获取用户的经历列表
     */
    List<ExperienceVO> listByUserId();

    /**
     * 根据类型获取用户的经历列表
     */
    List<ExperienceVO> listByUserIdAndType(ExperienceType type);

    /**
     * 获取实体
     */
    Experience getEntityById(Long id);

    /**
     * 审核经历 - 批准
     */
    void approve(Long id);

    /**
     * 审核经历 - 拒绝
     */
    void reject(Long id);
}