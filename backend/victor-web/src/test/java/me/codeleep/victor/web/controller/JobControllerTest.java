package me.codeleep.victor.web.controller;

import me.codeleep.victor.core.dto.JobRequest;
import me.codeleep.victor.web.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 岗位控制器接口测试
 */
class JobControllerTest extends BaseApiTest {

    @Test
    @DisplayName("API-J-001: 创建岗位成功")
    void create_Success() throws Exception {
        // Given
        JobRequest request = new JobRequest();
        request.setName("Java开发工程师");
        request.setDescription("负责后端开发");

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Java开发工程师"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("API-J-002: 查询岗位列表")
    void list_Success() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/jobs")
                        .param("page", "0")
                        .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("API-J-003: 获取岗位详情成功")
    void getById_Success() throws Exception {
        // Given - 先创建一个岗位
        JobRequest createRequest = new JobRequest();
        createRequest.setName("详情测试岗位");
        createRequest.setDescription("岗位描述");

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/jobs/" + jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(jobId))
                .andExpect(jsonPath("$.data.name").value("详情测试岗位"));
    }

    @Test
    @DisplayName("API-J-004: 更新岗位成功")
    void update_Success() throws Exception {
        // Given - 先创建一个岗位
        JobRequest createRequest = new JobRequest();
        createRequest.setName("待更新岗位");
        createRequest.setDescription("岗位描述");

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // 更新请求
        JobRequest updateRequest = new JobRequest();
        updateRequest.setName("已更新岗位");
        updateRequest.setDescription("更新后的描述");

        // When & Then
        mockMvc.perform(withAuth(put("/api/v1/jobs/" + jobId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("已更新岗位"));
    }

    @Test
    @DisplayName("API-J-005: 删除岗位成功")
    void delete_Success() throws Exception {
        // Given - 先创建一个岗位
        JobRequest createRequest = new JobRequest();
        createRequest.setName("待删除岗位");
        createRequest.setDescription("岗位描述");

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(delete("/api/v1/jobs/" + jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("API-J-006: 审核通过岗位")
    void approve_Success() throws Exception {
        // Given - 先创建一个岗位
        JobRequest createRequest = new JobRequest();
        createRequest.setName("待审核岗位");
        createRequest.setDescription("岗位描述");

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/jobs/" + jobId + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("API-J-007: 审核拒绝岗位")
    void reject_Success() throws Exception {
        // Given - 先创建一个岗位
        JobRequest createRequest = new JobRequest();
        createRequest.setName("待拒绝岗位");
        createRequest.setDescription("岗位描述");

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/jobs/" + jobId + "/reject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
