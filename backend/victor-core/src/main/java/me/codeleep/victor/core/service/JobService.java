package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.codeleep.victor.core.dto.JobQueryRequest;
import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.core.dto.JobVO;
import me.codeleep.victor.core.entity.Job;

/**
 * 岗位服务接口
 */
public interface JobService {

    /**
     * 创建岗位
     */
    JobVO create(JobRequest request);

    /**
     * 根据ID获取岗位
     */
    JobVO getById(Long id);

    /**
     * 更新岗位
     */
    JobVO update(Long id, JobRequest request);

    /**
     * 删除岗位
     */
    void delete(Long id);

    /**
     * 分页查询岗位列表
     */
    Page<JobVO> list(JobQueryRequest request);

    /**
     * 获取实体
     */
    Job getEntityById(Long id);

    /**
     * 审核岗位 - 批准
     */
    void approve(Long id);

    /**
     * 审核岗位 - 拒绝
     */
    void reject(Long id);
}