package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.core.dto.JobQueryRequest;
import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.core.dto.JobVO;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.service.converter.JobConverter;
import me.codeleep.victor.core.service.impl.JobServiceImpl;
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
 * 岗位服务单元测试
 */
class JobServiceImplTest extends BaseServiceTest {

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobConverter jobConverter;

    @InjectMocks
    private JobServiceImpl jobService;

    @Override
    protected void doSetUp() {
        when(jobConverter.toEntity(any(JobRequest.class))).thenAnswer(invocation -> {
            JobRequest req = invocation.getArgument(0);
            Job job = new Job();
            job.setName(req.getName());
            job.setDescription(req.getDescription());
            job.setExperienceYears(req.getExperienceYears());
            job.setEducation(req.getEducation());
            job.setSalaryRange(req.getSalaryRange());
            return job;
        });

        when(jobConverter.toVO(any(Job.class))).thenAnswer(invocation -> {
            Job j = invocation.getArgument(0);
            JobVO vo = new JobVO();
            vo.setId(j.getId());
            vo.setName(j.getName());
            vo.setDescription(j.getDescription());
            vo.setExperienceYears(j.getExperienceYears());
            vo.setEducation(j.getEducation());
            vo.setSalaryRange(j.getSalaryRange());
            return vo;
        });
    }

    @Test
    @DisplayName("UT-RES-002: 创建岗位成功")
    void createJob_Success() {
        // Given
        JobRequest request = new JobRequest();
        request.setName("Java开发工程师");
        request.setDescription("负责后端开发");

        when(jobMapper.insert(any(Job.class))).thenReturn(1);

        // When
        JobVO result = jobService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("Java开发工程师", result.getName());
        verify(jobMapper, times(1)).insert(any(Job.class));
    }

    @Test
    @DisplayName("创建岗位-设置正确的用户ID")
    void createJob_SetsUserId() {
        // Given
        JobRequest request = new JobRequest();
        request.setName("测试岗位");

        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            assertEquals(TEST_USER_ID, job.getUserId());
            return 1;
        });

        // When
        jobService.create(request);

        // Then
        verify(jobMapper, times(1)).insert(any(Job.class));
    }

    @Test
    @DisplayName("获取岗位详情成功")
    void getById_Success() {
        // Given
        Long jobId = 1L;
        Job job = createTestJob(jobId);
        when(jobMapper.selectById(jobId)).thenReturn(job);

        // When
        JobVO result = jobService.getById(jobId);

        // Then
        assertNotNull(result);
        assertEquals(jobId, result.getId());
        assertEquals("Java开发工程师", result.getName());
    }

    @Test
    @DisplayName("获取岗位详情-岗位不存在")
    void getById_NotFound() {
        // Given
        Long jobId = 999L;
        when(jobMapper.selectById(jobId)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> jobService.getById(jobId));
    }

    @Test
    @DisplayName("更新岗位成功")
    void update_Success() {
        // Given
        Long jobId = 1L;
        Job existing = createTestJob(jobId);
        when(jobMapper.selectById(jobId)).thenReturn(existing);
        when(jobMapper.updateById(any(Job.class))).thenReturn(1);

        JobRequest request = new JobRequest();
        request.setName("高级Java开发工程师");

        // When
        JobVO result = jobService.update(jobId, request);

        // Then
        assertNotNull(result);
        verify(jobMapper, times(1)).updateById(any(Job.class));
    }

    @Test
    @DisplayName("更新岗位-无权修改他人岗位")
    void update_Forbidden() {
        // Given
        Long jobId = 1L;
        Job existing = createTestJob(jobId);
        existing.setUserId(999L);
        when(jobMapper.selectById(jobId)).thenReturn(existing);

        JobRequest request = new JobRequest();
        request.setName("更新后的岗位");

        // When & Then
        assertThrows(BusinessException.class, () -> jobService.update(jobId, request));
    }

    @Test
    @DisplayName("删除岗位成功")
    void delete_Success() {
        // Given
        Long jobId = 1L;
        Job existing = createTestJob(jobId);
        when(jobMapper.selectById(jobId)).thenReturn(existing);
        when(jobMapper.deleteById(jobId)).thenReturn(1);

        // When
        jobService.delete(jobId);

        // Then
        verify(jobMapper, times(1)).deleteById(jobId);
    }

    @Test
    @DisplayName("审核通过岗位")
    void approve_Success() {
        // Given
        Long jobId = 1L;
        Job existing = createTestJob(jobId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(jobMapper.selectById(jobId)).thenReturn(existing);
        when(jobMapper.updateById(any(Job.class))).thenReturn(1);

        // When
        jobService.approve(jobId);

        // Then
        verify(jobMapper, times(1)).updateById(any(Job.class));
    }

    @Test
    @DisplayName("审核拒绝岗位")
    void reject_Success() {
        // Given
        Long jobId = 1L;
        Job existing = createTestJob(jobId);
        existing.setIngestStatus(IngestStatus.PENDING_REVIEW);
        when(jobMapper.selectById(jobId)).thenReturn(existing);
        when(jobMapper.updateById(any(Job.class))).thenReturn(1);

        // When
        jobService.reject(jobId);

        // Then
        verify(jobMapper, times(1)).updateById(any(Job.class));
    }

    @Test
    @DisplayName("分页查询岗位列表")
    void list_Success() {
        // Given
        JobQueryRequest request = new JobQueryRequest();
        request.setPage(0);
        request.setSize(10);

        List<Job> jobs = Arrays.asList(
                createTestJob(1L),
                createTestJob(2L)
        );

        Page<Job> page = new Page<>(1, 10);
        page.setRecords(jobs);
        page.setTotal(2);

        when(jobMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        // When
        Page<JobVO> result = jobService.list(request);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
    }

    private Job createTestJob(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setUserId(TEST_USER_ID);
        job.setName("Java开发工程师");
        job.setDescription("负责后端开发");
        job.setIngestStatus(IngestStatus.ACTIVE);
        return job;
    }
}
