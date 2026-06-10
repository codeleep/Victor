package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.QuestionSource;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.QuestionQueryRequest;
import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.core.dto.QuestionVO;
import me.codeleep.victor.core.entity.Question;
import me.codeleep.victor.core.mapper.QuestionMapper;
import me.codeleep.victor.core.service.QuestionService;
import me.codeleep.victor.core.service.converter.QuestionConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 题目服务实现
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionConverter questionConverter;

    @Override
    @Transactional
    public QuestionVO create(QuestionRequest request) {
        Question question = questionConverter.toEntity(request);
        question.setUserId(UserContext.getUserId());
        question.setSource(QuestionSource.USER);
        questionMapper.insert(question);
        return questionConverter.toVO(question);
    }

    @Override
    public QuestionVO getById(Long id) {
        Question question = getEntityById(id);
        return questionConverter.toVO(question);
    }

    @Override
    @Transactional
    public QuestionVO update(Long id, QuestionRequest request) {
        Question question = getEntityById(id);
        // 验证用户权限
        if (!question.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改此题目");
        }
        // 使用新实体只更新非空字段，避免JSONB类型处理问题
        Question updateEntity = new Question();
        updateEntity.setId(id);
        if (request.getTitle() != null) updateEntity.setTitle(request.getTitle());
        if (request.getDescription() != null) updateEntity.setDescription(request.getDescription());
        if (request.getType() != null) updateEntity.setType(request.getType());
        if (request.getDifficulty() != null) updateEntity.setDifficulty(request.getDifficulty());
        if (request.getReferenceAnswer() != null) updateEntity.setReferenceAnswer(request.getReferenceAnswer());
        questionMapper.updateById(updateEntity);
        return getById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Question question = getEntityById(id);
        // 验证用户权限
        if (!question.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此题目");
        }
        questionMapper.deleteById(id);
    }

    @Override
    public Page<QuestionVO> list(QuestionQueryRequest request) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getType() != null, Question::getType, request.getType())
                .eq(request.getDifficulty() != null, Question::getDifficulty, request.getDifficulty())
                .like(request.getTag() != null && !request.getTag().isEmpty(), Question::getTags, request.getTag())
                .eq(Question::getUserId, UserContext.getUserId())
                .orderByDesc(Question::getCreatedAt);

        Page<Question> page = new Page<>(request.getPage() + 1, request.getSize());
        Page<Question> questionPage = questionMapper.selectPage(page, wrapper);

        Page<QuestionVO> voPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        List<QuestionVO> voList = questionPage.getRecords().stream()
                .map(questionConverter::toVO)
                .toList();
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Question getEntityById(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ResultCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    @Override
    @Transactional
    public void approve(Long id) {
        getEntityById(id); // 验证存在
        Question updateEntity = new Question();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.ACTIVE);
        questionMapper.updateById(updateEntity);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        getEntityById(id); // 验证存在
        Question updateEntity = new Question();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.REJECTED);
        questionMapper.updateById(updateEntity);
    }

}