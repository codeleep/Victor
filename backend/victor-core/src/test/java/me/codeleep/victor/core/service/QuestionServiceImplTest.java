package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.QuestionSource;
import me.codeleep.victor.common.enums.QuestionType;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.core.dto.QuestionQueryRequest;
import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.core.dto.QuestionVO;
import me.codeleep.victor.core.entity.Question;
import me.codeleep.victor.core.mapper.QuestionMapper;
import me.codeleep.victor.core.service.converter.QuestionConverter;
import me.codeleep.victor.core.service.impl.QuestionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 题目服务单元测试
 */
class QuestionServiceImplTest extends BaseServiceTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionConverter questionConverter;

    @InjectMocks
    private QuestionServiceImpl questionService;

    @Override
    protected void doSetUp() {
        when(questionConverter.toEntity(any(QuestionRequest.class))).thenAnswer(invocation -> {
            QuestionRequest req = invocation.getArgument(0);
            Question q = new Question();
            q.setTitle(req.getTitle());
            q.setDescription(req.getDescription());
            q.setType(req.getType());
            q.setDifficulty(req.getDifficulty());
            q.setReferenceAnswer(req.getReferenceAnswer());
            return q;
        });

        when(questionConverter.toVO(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            QuestionVO vo = new QuestionVO();
            vo.setId(q.getId());
            vo.setTitle(q.getTitle());
            vo.setDescription(q.getDescription());
            vo.setType(q.getType());
            vo.setDifficulty(q.getDifficulty());
            vo.setReferenceAnswer(q.getReferenceAnswer());
            vo.setSource(q.getSource());
            return vo;
        });
    }

    @Test
    @DisplayName("UT-RES-001: 创建题目成功")
    void createQuestion_Success() {
        // Given
        QuestionRequest request = new QuestionRequest();
        request.setTitle("测试题目");
        request.setDescription("题目描述");
        request.setType(QuestionType.TECHNICAL);
        request.setDifficulty(Difficulty.MEDIUM);

        when(questionMapper.insert(any(Question.class))).thenReturn(1);

        // When
        QuestionVO result = questionService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("测试题目", result.getTitle());
        verify(questionMapper, times(1)).insert(any(Question.class));
    }

    @Test
    @DisplayName("UT-RES-001: 创建题目-设置正确的用户ID和来源")
    void createQuestion_SetsUserIdAndSource() {
        // Given
        QuestionRequest request = new QuestionRequest();
        request.setTitle("测试题目");

        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            assertEquals(TEST_USER_ID, question.getUserId());
            assertEquals(QuestionSource.USER, question.getSource());
            return 1;
        });

        // When
        questionService.create(request);

        // Then
        verify(questionMapper, times(1)).insert(any(Question.class));
    }

    @Test
    @DisplayName("获取题目详情成功")
    void getById_Success() {
        // Given
        Long questionId = 1L;
        Question question = createTestQuestion(questionId);
        when(questionMapper.selectById(questionId)).thenReturn(question);

        // When
        QuestionVO result = questionService.getById(questionId);

        // Then
        assertNotNull(result);
        assertEquals(questionId, result.getId());
        assertEquals("测试题目", result.getTitle());
    }

    @Test
    @DisplayName("获取题目详情-题目不存在")
    void getById_NotFound() {
        // Given
        Long questionId = 999L;
        when(questionMapper.selectById(questionId)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> questionService.getById(questionId));
    }

    @Test
    @DisplayName("更新题目成功")
    void update_Success() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        when(questionMapper.selectById(questionId)).thenReturn(existing);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);

        QuestionRequest request = new QuestionRequest();
        request.setTitle("更新后的题目");
        request.setDescription("更新后的描述");

        // When
        QuestionVO result = questionService.update(questionId, request);

        // Then
        assertNotNull(result);
        verify(questionMapper, times(1)).updateById(any(Question.class));
    }

    @Test
    @DisplayName("更新题目-无权修改他人题目")
    void update_Forbidden() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        existing.setUserId(999L); // 不同的用户ID
        when(questionMapper.selectById(questionId)).thenReturn(existing);

        QuestionRequest request = new QuestionRequest();
        request.setTitle("更新后的题目");

        // When & Then
        assertThrows(BusinessException.class, () -> questionService.update(questionId, request));
    }

    @Test
    @DisplayName("删除题目成功")
    void delete_Success() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        when(questionMapper.selectById(questionId)).thenReturn(existing);
        when(questionMapper.deleteById(questionId)).thenReturn(1);

        // When
        questionService.delete(questionId);

        // Then
        verify(questionMapper, times(1)).deleteById(questionId);
    }

    @Test
    @DisplayName("删除题目-无权删除他人题目")
    void delete_Forbidden() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        existing.setUserId(999L); // 不同的用户ID
        when(questionMapper.selectById(questionId)).thenReturn(existing);

        // When & Then
        assertThrows(BusinessException.class, () -> questionService.delete(questionId));
    }

    @Test
    @DisplayName("审核通过题目")
    void approve_Success() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(questionMapper.selectById(questionId)).thenReturn(existing);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);

        // When
        questionService.approve(questionId);

        // Then
        verify(questionMapper, times(1)).updateById(any(Question.class));
    }

    @Test
    @DisplayName("审核拒绝题目")
    void reject_Success() {
        // Given
        Long questionId = 1L;
        Question existing = createTestQuestion(questionId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(questionMapper.selectById(questionId)).thenReturn(existing);
        when(questionMapper.updateById(any(Question.class))).thenReturn(1);

        // When
        questionService.reject(questionId);

        // Then
        verify(questionMapper, times(1)).updateById(any(Question.class));
    }

    @Test
    @DisplayName("分页查询题目列表")
    void list_Success() {
        // Given
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setPage(0);
        request.setSize(10);

        List<Question> questions = Arrays.asList(
                createTestQuestion(1L),
                createTestQuestion(2L)
        );

        Page<Question> page = new Page<>(1, 10);
        page.setRecords(questions);
        page.setTotal(2);

        when(questionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        // When
        Page<QuestionVO> result = questionService.list(request);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2, result.getTotal());
    }

    private Question createTestQuestion(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setUserId(TEST_USER_ID);
        question.setTitle("测试题目");
        question.setDescription("题目描述");
        question.setType(QuestionType.TECHNICAL);
        question.setDifficulty(Difficulty.MEDIUM);
        question.setSource(QuestionSource.USER);
        question.setIngestStatus(IngestStatus.ACTIVE);
        return question;
    }
}
