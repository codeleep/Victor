package me.codeleep.victor.core.service;

import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历服务接口
 */
public interface ResumeService {

    /**
     * 上传简历
     */
    ResumeVO upload(Long userId, String name, MultipartFile file);

    /**
     * 解析简历
     */
    void parse(Long resumeId);

    /**
     * 根据ID获取简历
     */
    ResumeVO getById(Long id);

    /**
     * 获取用户的简历列表
     */
    List<ResumeVO> listByUserId();

    /**
     * 删除简历
     */
    void delete(Long id);

    /**
     * 获取实体
     */
    Resume getEntityById(Long id);

    /**
     * 审核简历 - 批准
     */
    void approve(Long id);

    /**
     * 更新简历解析文本
     */
    void updateRawText(Long id, String rawText);

    /**
     * 审核简历 - 拒绝
     */
    void reject(Long id);
}