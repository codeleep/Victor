package me.codeleep.victor.core.service;

import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.common.enums.ExperienceType;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.core.dto.ExperienceRequest;
import me.codeleep.victor.core.dto.ExperienceVO;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import me.codeleep.victor.core.service.converter.ExperienceConverter;
import me.codeleep.victor.core.service.impl.ExperienceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 经历服务单元测试
 */
class ExperienceServiceImplTest extends BaseServiceTest {

    @Mock
    private ExperienceMapper experienceMapper;

    @Mock
    private ExperienceConverter experienceConverter;

    @InjectMocks
    private ExperienceServiceImpl experienceService;

    @Override
    protected void doSetUp() {
        // stub toEntity: 返回一个可操作的Experience实体
        when(experienceConverter.toEntity(any(ExperienceRequest.class))).thenAnswer(invocation -> {
            ExperienceRequest req = invocation.getArgument(0);
            Experience exp = new Experience();
            exp.setTitle(req.getTitle());
            exp.setDescription(req.getDescription());
            exp.setType(req.getType());
            exp.setStartDate(req.getStartDate());
            exp.setEndDate(req.getEndDate());
            return exp;
        });

        // stub toVO: 返回一个包含关键字段的ExperienceVO
        when(experienceConverter.toVO(any(Experience.class))).thenAnswer(invocation -> {
            Experience e = invocation.getArgument(0);
            ExperienceVO vo = new ExperienceVO();
            vo.setId(e.getId());
            vo.setTitle(e.getTitle());
            vo.setDescription(e.getDescription());
            vo.setType(e.getType());
            vo.setStartDate(e.getStartDate());
            vo.setEndDate(e.getEndDate());
            return vo;
        });
    }

    @Test
    @DisplayName("UT-RES-005: 创建经历成功")
    void create_Success() {
        // Given
        ExperienceRequest request = new ExperienceRequest();
        request.setTitle("Java开发工程师");
        request.setDescription("负责后端开发");
        request.setType(ExperienceType.WORK);
        request.setStartDate(LocalDate.of(2020, 1, 1));

        when(experienceMapper.insert(any(Experience.class))).thenReturn(1);

        // When
        ExperienceVO result = experienceService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("Java开发工程师", result.getTitle());
        verify(experienceMapper, times(1)).insert(any(Experience.class));
    }

    @Test
    @DisplayName("创建经历-设置正确的用户ID")
    void create_SetsUserId() {
        // Given
        ExperienceRequest request = new ExperienceRequest();
        request.setTitle("测试经历");

        when(experienceMapper.insert(any(Experience.class))).thenAnswer(invocation -> {
            Experience experience = invocation.getArgument(0);
            assertEquals(TEST_USER_ID, experience.getUserId());
            return 1;
        });

        // When
        experienceService.create(request);

        // Then
        verify(experienceMapper, times(1)).insert(any(Experience.class));
    }

    @Test
    @DisplayName("获取经历详情成功")
    void getById_Success() {
        // Given
        Long experienceId = 1L;
        Experience experience = createTestExperience(experienceId);
        when(experienceMapper.selectById(experienceId)).thenReturn(experience);

        // When
        ExperienceVO result = experienceService.getById(experienceId);

        // Then
        assertNotNull(result);
        assertEquals(experienceId, result.getId());
        assertEquals("Java开发工程师", result.getTitle());
    }

    @Test
    @DisplayName("获取经历详情-经历不存在")
    void getById_NotFound() {
        // Given
        Long experienceId = 999L;
        when(experienceMapper.selectById(experienceId)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> experienceService.getById(experienceId));
    }

    @Test
    @DisplayName("更新经历成功")
    void update_Success() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);
        when(experienceMapper.updateById(any(Experience.class))).thenReturn(1);

        ExperienceRequest request = new ExperienceRequest();
        request.setTitle("高级Java开发工程师");

        // When
        ExperienceVO result = experienceService.update(experienceId, request);

        // Then
        assertNotNull(result);
        verify(experienceMapper, times(1)).updateById(any(Experience.class));
    }

    @Test
    @DisplayName("更新经历-无权修改他人经历")
    void update_Forbidden() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        existing.setUserId(999L);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);

        ExperienceRequest request = new ExperienceRequest();
        request.setTitle("更新后的经历");

        // When & Then
        assertThrows(BusinessException.class, () -> experienceService.update(experienceId, request));
    }

    @Test
    @DisplayName("删除经历成功")
    void delete_Success() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);
        when(experienceMapper.deleteById(experienceId)).thenReturn(1);

        // When
        experienceService.delete(experienceId);

        // Then
        verify(experienceMapper, times(1)).deleteById(experienceId);
    }

    @Test
    @DisplayName("删除经历-无权删除他人经历")
    void delete_Forbidden() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        existing.setUserId(999L);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);

        // When & Then
        assertThrows(BusinessException.class, () -> experienceService.delete(experienceId));
    }

    @Test
    @DisplayName("获取当前用户经历列表")
    void listByUserId_Success() {
        // Given
        List<Experience> experiences = Arrays.asList(
                createTestExperience(1L),
                createTestExperience(2L)
        );
        when(experienceMapper.selectList(any())).thenReturn(experiences);

        // When
        List<ExperienceVO> result = experienceService.listByUserId();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("按类型查询经历列表")
    void listByUserIdAndType_Success() {
        // Given
        List<Experience> experiences = Arrays.asList(
                createTestExperience(1L)
        );
        when(experienceMapper.selectList(any())).thenReturn(experiences);

        // When
        List<ExperienceVO> result = experienceService.listByUserIdAndType(ExperienceType.WORK);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("审核通过经历")
    void approve_Success() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);
        when(experienceMapper.updateById(any(Experience.class))).thenReturn(1);

        // When
        experienceService.approve(experienceId);

        // Then
        assertEquals(IngestStatus.ACTIVE, existing.getIngestStatus());
        verify(experienceMapper, times(1)).updateById(existing);
    }

    @Test
    @DisplayName("审核拒绝经历")
    void reject_Success() {
        // Given
        Long experienceId = 1L;
        Experience existing = createTestExperience(experienceId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(experienceMapper.selectById(experienceId)).thenReturn(existing);
        when(experienceMapper.updateById(any(Experience.class))).thenReturn(1);

        // When
        experienceService.reject(experienceId);

        // Then
        assertEquals(IngestStatus.REJECTED, existing.getIngestStatus());
        verify(experienceMapper, times(1)).updateById(existing);
    }

    private Experience createTestExperience(Long id) {
        Experience experience = new Experience();
        experience.setId(id);
        experience.setUserId(TEST_USER_ID);
        experience.setTitle("Java开发工程师");
        experience.setDescription("负责后端开发");
        experience.setType(ExperienceType.WORK);
        experience.setStartDate(LocalDate.of(2020, 1, 1));
        experience.setIngestStatus(IngestStatus.ACTIVE);
        return experience;
    }
}
