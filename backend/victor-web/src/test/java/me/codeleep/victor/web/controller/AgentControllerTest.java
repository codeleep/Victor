package me.codeleep.victor.web.controller;

import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.web.BaseApiTest;
import me.codeleep.victor.common.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Agent控制器接口测试
 */
class AgentControllerTest extends BaseApiTest {

    @Test
    @DisplayName("API-AGT-001: 创建Agent成功")
    void create_Success() throws Exception {
        // Given
        AgentRequest request = new AgentRequest();
        request.setName("测试Agent");
        request.setRole("interviewer");
        request.setSystemPrompt("你是一位面试官");
        request.setType(AgentType.INTERVIEW);

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/agents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试Agent"))
                .andExpect(jsonPath("$.data.role").value("interviewer"))
                .andExpect(jsonPath("$.data.key").isNotEmpty());
    }

    @Test
    @DisplayName("API-AGT-002: 查询Agent列表")
    void list_Success() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/agents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("API-AGT-003: 获取Agent详情成功")
    void getById_Success() throws Exception {
        // Given - 先创建一个Agent
        AgentRequest createRequest = new AgentRequest();
        createRequest.setName("详情测试Agent");
        createRequest.setRole("evaluator");
        createRequest.setSystemPrompt("你是一位评估专家");
        createRequest.setType(AgentType.INTERVIEW);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/agents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long agentId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/agents/" + agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(agentId))
                .andExpect(jsonPath("$.data.name").value("详情测试Agent"));
    }

    @Test
    @DisplayName("API-AGT-004: 更新Agent成功")
    void update_Success() throws Exception {
        // Given - 先创建一个Agent
        AgentRequest createRequest = new AgentRequest();
        createRequest.setName("待更新Agent");
        createRequest.setRole("interviewer");
        createRequest.setSystemPrompt("你是一位面试官");
        createRequest.setType(AgentType.INTERVIEW);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/agents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long agentId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // 更新请求
        AgentRequest updateRequest = new AgentRequest();
        updateRequest.setName("已更新Agent");
        updateRequest.setRole("evaluator");
        updateRequest.setSystemPrompt("你是一位评估专家");
        updateRequest.setType(AgentType.INTERVIEW);

        // When & Then
        mockMvc.perform(withAuth(put("/api/v1/agents/" + agentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("已更新Agent"));
    }

    @Test
    @DisplayName("API-AGT-005: 删除Agent成功")
    void delete_Success() throws Exception {
        // Given - 先创建一个Agent
        AgentRequest createRequest = new AgentRequest();
        createRequest.setName("待删除Agent");
        createRequest.setRole("interviewer");
        createRequest.setSystemPrompt("你是一位面试官");
        createRequest.setType(AgentType.INTERVIEW);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/agents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long agentId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(delete("/api/v1/agents/" + agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("按类型查询Agent列表")
    void list_FilterByType() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/agents"))
                        .param("type", "INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
