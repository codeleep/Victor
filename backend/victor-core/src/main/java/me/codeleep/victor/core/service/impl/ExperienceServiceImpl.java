package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.enums.ExperienceType;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.ExperienceRequest;
import me.codeleep.victor.core.dto.ExperienceVO;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import me.codeleep.victor.core.service.ExperienceService;
import me.codeleep.victor.core.service.converter.ExperienceConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 经历服务实现
 */
@Service
@RequiredArgsConstructor
public class ExperienceServiceImpl implements ExperienceService {

    private final ExperienceMapper experienceMapper;
    private final ExperienceConverter experienceConverter;

    @Override
    @Transactional
    public ExperienceVO create(ExperienceRequest request) {
        Experience experience = experienceConverter.toEntity(request);
        experience.setUserId(UserContext.getUserId());
        experienceMapper.insert(experience);
        return experienceConverter.toVO(experience);
    }

    @Override
    public ExperienceVO getById(Long id) {
        Experience experience = getEntityById(id);
        return experienceConverter.toVO(experience);
    }

    @Override
    @Transactional
    public ExperienceVO update(Long id, ExperienceRequest request) {
        Experience experience = getEntityById(id);
        // 验证用户权限
        if (!experience.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改此经历");
        }
        experienceConverter.updateEntity(request, experience);
        experienceMapper.updateById(experience);
        return experienceConverter.toVO(experience);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Experience experience = getEntityById(id);
        // 验证用户权限
        if (!experience.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此经历");
        }
        experienceMapper.deleteById(id);
    }

    @Override
    public List<ExperienceVO> listByUserId() {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getUserId, UserContext.getUserId())
                .orderByDesc(Experience::getStartDate);
        List<Experience> experiences = experienceMapper.selectList(wrapper);
        return experiences.stream().map(experienceConverter::toVO).toList();
    }

    @Override
    public List<ExperienceVO> listByUserIdAndType(ExperienceType type) {
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getUserId, UserContext.getUserId())
                .eq(type != null, Experience::getType, type)
                .orderByDesc(Experience::getStartDate);
        List<Experience> experiences = experienceMapper.selectList(wrapper);
        return experiences.stream().map(experienceConverter::toVO).toList();
    }

    @Override
    public Experience getEntityById(Long id) {
        Experience experience = experienceMapper.selectById(id);
        if (experience == null) {
            throw new BusinessException(ResultCode.EXPERIENCE_NOT_FOUND);
        }
        return experience;
    }

    @Override
    @Transactional
    public void approve(Long id) {
        Experience experience = getEntityById(id);
        experience.setIngestStatus(IngestStatus.ACTIVE);
        experienceMapper.updateById(experience);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        Experience experience = getEntityById(id);
        experience.setIngestStatus(IngestStatus.REJECTED);
        experienceMapper.updateById(experience);
    }

}