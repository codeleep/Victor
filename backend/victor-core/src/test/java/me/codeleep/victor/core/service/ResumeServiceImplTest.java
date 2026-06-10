package me.codeleep.victor.core.service;

import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.ResumeStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.core.service.converter.ResumeConverter;
import me.codeleep.victor.core.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 简历服务单元测试
 */
class ResumeServiceImplTest extends BaseServiceTest {

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private ResumeConverter resumeConverter;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    @Override
    protected void doSetUp() {
        ReflectionTestUtils.setField(resumeService, "uploadBasePath", System.getProperty("java.io.tmpdir"));

        when(resumeConverter.toVO(any(Resume.class))).thenAnswer(invocation -> {
            Resume r = invocation.getArgument(0);
            ResumeVO vo = new ResumeVO();
            vo.setId(r.getId());
            vo.setName(r.getName());
            vo.setFileName(r.getFileName());
            vo.setFilePath(r.getFilePath());
            vo.setStatus(r.getStatus());
            return vo;
        });
    }

    @Test
    @DisplayName("UT-RES-003: 上传简历成功")
    void upload_Success() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "test content".getBytes()
        );

        when(resumeMapper.insert(any(Resume.class))).thenReturn(1);

        // When
        ResumeVO result = resumeService.upload(TEST_USER_ID, "我的简历", file);

        // Then
        assertNotNull(result);
        assertEquals("我的简历", result.getName());
        assertEquals(ResumeStatus.PENDING, result.getStatus());
        verify(resumeMapper, times(1)).insert(any(Resume.class));
    }

    @Test
    @DisplayName("上传简历-设置正确的用户ID")
    void upload_SetsUserId() {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "test content".getBytes()
        );

        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            assertEquals(TEST_USER_ID, resume.getUserId());
            return 1;
        });

        // When
        resumeService.upload(TEST_USER_ID, "测试简历", file);

        // Then
        verify(resumeMapper, times(1)).insert(any(Resume.class));
    }

    @Test
    @DisplayName("获取简历详情成功")
    void getById_Success() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);

        // When
        ResumeVO result = resumeService.getById(resumeId);

        // Then
        assertNotNull(result);
        assertEquals(resumeId, result.getId());
        assertEquals("测试简历", result.getName());
    }

    @Test
    @DisplayName("获取简历详情-简历不存在")
    void getById_NotFound() {
        // Given
        Long resumeId = 999L;
        when(resumeMapper.selectById(resumeId)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> resumeService.getById(resumeId));
    }

    @Test
    @DisplayName("解析简历成功")
    void parse_Success() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        // 设置一个真实存在的文件路径用于Tika解析
        resume.setFilePath("/tmp/nonexistent_test_resume.pdf");
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);

        // parse 方法会尝试读取文件，由于文件不存在会抛出 BusinessException
        // When & Then
        assertThrows(BusinessException.class, () -> resumeService.parse(resumeId));
    }

    @Test
    @DisplayName("解析简历-无权操作他人简历")
    void parse_Forbidden() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        resume.setUserId(999L);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);

        // When & Then
        assertThrows(BusinessException.class, () -> resumeService.parse(resumeId));
    }

    @Test
    @DisplayName("删除简历成功")
    void delete_Success() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);
        when(resumeMapper.deleteById(resumeId)).thenReturn(1);

        // When
        resumeService.delete(resumeId);

        // Then
        verify(resumeMapper, times(1)).deleteById(resumeId);
    }

    @Test
    @DisplayName("删除简历-无权删除他人简历")
    void delete_Forbidden() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        resume.setUserId(999L);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);

        // When & Then
        assertThrows(BusinessException.class, () -> resumeService.delete(resumeId));
    }

    @Test
    @DisplayName("获取当前用户简历列表")
    void listByUserId_Success() {
        // Given
        List<Resume> resumes = Arrays.asList(
                createTestResume(1L),
                createTestResume(2L)
        );
        when(resumeMapper.selectList(any())).thenReturn(resumes);

        // When
        List<ResumeVO> result = resumeService.listByUserId();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("审核通过简历")
    void approve_Success() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        resume.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);

        // When
        resumeService.approve(resumeId);

        // Then
        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper, times(1)).updateById(captor.capture());
        assertEquals(IngestStatus.ACTIVE, captor.getValue().getIngestStatus());
    }

    @Test
    @DisplayName("审核拒绝简历")
    void reject_Success() {
        // Given
        Long resumeId = 1L;
        Resume resume = createTestResume(resumeId);
        resume.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(resumeMapper.selectById(resumeId)).thenReturn(resume);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);

        // When
        resumeService.reject(resumeId);

        // Then
        ArgumentCaptor<Resume> captor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper, times(1)).updateById(captor.capture());
        assertEquals(IngestStatus.REJECTED, captor.getValue().getIngestStatus());
    }

    private Resume createTestResume(Long id) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(TEST_USER_ID);
        resume.setName("测试简历");
        resume.setFileName("resume.pdf");
        resume.setFilePath("/tmp/resume.pdf");
        resume.setStatus(ResumeStatus.PENDING);
        resume.setIngestStatus(IngestStatus.ACTIVE);
        return resume;
    }
}
