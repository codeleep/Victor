package me.codeleep.victor.web.controller;

import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.web.BaseApiTest;
import me.codeleep.victor.common.enums.Difficulty;
import me.codeleep.victor.common.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 题目控制器接口测试
 */
class QuestionControllerTest extends BaseApiTest {

    @Test
    @DisplayName("API-Q-001: 创建题目成功")
    void create_Success() throws Exception {
        // Given
        QuestionRequest request = new QuestionRequest();
        request.setTitle("测试题目");
        request.setDescription("题目描述");
        request.setType(QuestionType.TECHNICAL);
        request.setDifficulty(Difficulty.MEDIUM);

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试题目"))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    @DisplayName("API-Q-002: 创建题目-缺少必填字段")
    void create_MissingTitle() throws Exception {
        // Given
        QuestionRequest request = new QuestionRequest();
        request.setDescription("题目描述");
        // title为空

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API-Q-006: 获取题目详情成功")
    void getById_Success() throws Exception {
        // Given - 先创建一个题目
        QuestionRequest createRequest = new QuestionRequest();
        createRequest.setTitle("详情测试题目");
        createRequest.setDescription("描述");
        createRequest.setType(QuestionType.TECHNICAL);
        createRequest.setDifficulty(Difficulty.EASY);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long questionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/questions/" + questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(questionId))
                .andExpect(jsonPath("$.data.title").value("详情测试题目"));
    }

    @Test
    @DisplayName("API-Q-007: 获取题目详情-题目不存在")
    void getById_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/questions/999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }

    @Test
    @DisplayName("API-Q-008: 更新题目成功")
    void update_Success() throws Exception {
        // Given - 先创建一个题目
        QuestionRequest createRequest = new QuestionRequest();
        createRequest.setTitle("待更新题目");
        createRequest.setDescription("描述");
        createRequest.setType(QuestionType.TECHNICAL);
        createRequest.setDifficulty(Difficulty.MEDIUM);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long questionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // 更新请求
        QuestionRequest updateRequest = new QuestionRequest();
        updateRequest.setTitle("已更新题目");
        updateRequest.setDescription("更新后的描述");
        updateRequest.setType(QuestionType.BEHAVIORAL);
        updateRequest.setDifficulty(Difficulty.HARD);

        // When & Then
        mockMvc.perform(withAuth(put("/api/v1/questions/" + questionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("已更新题目"));
    }

    @Test
    @DisplayName("API-Q-009: 删除题目成功")
    void delete_Success() throws Exception {
        // Given - 先创建一个题目
        QuestionRequest createRequest = new QuestionRequest();
        createRequest.setTitle("待删除题目");
        createRequest.setDescription("描述");
        createRequest.setType(QuestionType.TECHNICAL);
        createRequest.setDifficulty(Difficulty.MEDIUM);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long questionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(delete("/api/v1/questions/" + questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证已删除
        mockMvc.perform(withAuth(get("/api/v1/questions/" + questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }

    @Test
    @DisplayName("API-Q-003: 查询题目列表")
    void list_Success() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/questions")
                        .param("page", "0")
                        .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("API-Q-010: 审核通过题目")
    void approve_Success() throws Exception {
        // Given - 先创建一个题目
        QuestionRequest createRequest = new QuestionRequest();
        createRequest.setTitle("待审核题目");
        createRequest.setDescription("描述");
        createRequest.setType(QuestionType.TECHNICAL);
        createRequest.setDifficulty(Difficulty.MEDIUM);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long questionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/questions/" + questionId + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("API-Q-011: 审核拒绝题目")
    void reject_Success() throws Exception {
        // Given - 先创建一个题目
        QuestionRequest createRequest = new QuestionRequest();
        createRequest.setTitle("待拒绝题目");
        createRequest.setDescription("描述");
        createRequest.setType(QuestionType.TECHNICAL);
        createRequest.setDifficulty(Difficulty.MEDIUM);

        MvcResult createResult = mockMvc.perform(withAuth(post("/api/v1/questions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Long questionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // When & Then
        mockMvc.perform(withAuth(post("/api/v1/questions/" + questionId + "/reject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("API-Q-004: 按类型筛选题目")
    void list_FilterByType() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/questions"))
                        .param("type", "TECHNICAL")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("API-Q-005: 按难度筛选题目")
    void list_FilterByDifficulty() throws Exception {
        // When & Then
        mockMvc.perform(withAuth(get("/api/v1/questions"))
                        .param("difficulty", "HARD")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
