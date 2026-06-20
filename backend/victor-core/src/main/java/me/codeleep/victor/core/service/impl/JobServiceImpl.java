package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.SourceType;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.JobQueryRequest;
import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.core.dto.JobVO;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.service.JobService;
import me.codeleep.victor.core.service.converter.JobConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位服务实现
 */
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobMapper jobMapper;
    private final JobConverter jobConverter;

    @Override
    @Transactional
    public JobVO create(JobRequest request) {
        Job job = jobConverter.toEntity(request);
        job.setUserId(UserContext.getUserId());
        
        // 根据认证方式设置来源和导入状态
        if (UserContext.isApiKeyAuth()) {
            job.setSourceType(SourceType.OPEN_API);
            job.setSourceApiKeyId(UserContext.getApiKeyId());
            // 根据API Key配置设置默认导入状态
            String defaultStatus = UserContext.getDefaultIngestStatus();
            if ("PENDING_REVIEW".equals(defaultStatus)) {
                job.setIngestStatus(IngestStatus.PENDING_REVIEW);
            } else {
                job.setIngestStatus(IngestStatus.ACTIVE);
            }
        } else {
            job.setSourceType(SourceType.USER);
            job.setIngestStatus(IngestStatus.ACTIVE);
        }
        
        jobMapper.insert(job);
        return jobConverter.toVO(job);
    }

    @Override
    public JobVO getById(Long id) {
        Job job = getEntityById(id);
        return jobConverter.toVO(job);
    }

    @Override
    @Transactional
    public JobVO update(Long id, JobRequest request) {
        Job job = getEntityById(id);
        // 验证用户权限
        if (!job.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改此岗位");
        }
        // 使用新实体只更新非空字段，避免JSONB类型处理问题
        Job updateEntity = new Job();
        updateEntity.setId(id);
        if (request.getName() != null) updateEntity.setName(request.getName());
        if (request.getDescription() != null) updateEntity.setDescription(request.getDescription());
        if (request.getExperienceYears() != null) updateEntity.setExperienceYears(request.getExperienceYears());
        if (request.getEducation() != null) updateEntity.setEducation(request.getEducation());
        if (request.getSalaryRange() != null) updateEntity.setSalaryRange(request.getSalaryRange());
        jobMapper.updateById(updateEntity);
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Job job = getEntityById(id);
        // 验证用户权限
        if (!job.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此岗位");
        }
        jobMapper.deleteById(id);
    }

    @Override
    public Page<JobVO> list(JobQueryRequest request) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getUserId, UserContext.getUserId())
                .orderByDesc(Job::getCreatedAt);

        Page<Job> page = new Page<>(request.getPage() + 1, request.getSize());
        Page<Job> jobPage = jobMapper.selectPage(page, wrapper);

        Page<JobVO> voPage = new Page<>(jobPage.getCurrent(), jobPage.getSize(), jobPage.getTotal());
        List<JobVO> voList = jobPage.getRecords().stream()
                .map(jobConverter::toVO)
                .toList();
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Job getEntityById(Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.JOB_NOT_FOUND);
        }
        return job;
    }

    @Override
    @Transactional
    public void approve(Long id) {
        getEntityById(id); // 验证存在
        Job updateEntity = new Job();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.ACTIVE);
        jobMapper.updateById(updateEntity);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        getEntityById(id); // 验证存在
        Job updateEntity = new Job();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.REJECTED);
        jobMapper.updateById(updateEntity);
    }

}
