package me.codeleep.victor.core.service;

import me.codeleep.victor.core.BaseServiceTest;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.core.dto.AgentRequest;
import me.codeleep.victor.core.dto.AgentVO;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.service.converter.AgentConverter;
import me.codeleep.victor.core.service.impl.AgentServiceImpl;
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
 * Agent服务单元测试
 */
class AgentServiceImplTest extends BaseServiceTest {

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentLlmConfigMapper agentLlmConfigMapper;

    @Mock
    private AgentConverter agentConverter;

    @InjectMocks
    private AgentServiceImpl agentService;

    @Override
    protected void doSetUp() {
        // stub toEntity: 返回一个可操作的Agent实体
        when(agentConverter.toEntity(any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest req = invocation.getArgument(0);
            Agent agent = new Agent();
            agent.setName(req.getName());
            agent.setRole(req.getRole());
            agent.setSystemPrompt(req.getSystemPrompt());
            agent.setType(req.getType());
            agent.setLlmConfigId(req.getLlmConfigId());
            return agent;
        });

        // stub toVO: 返回一个包含关键字段的AgentVO
        when(agentConverter.toVO(any(Agent.class))).thenAnswer(invocation -> {
            Agent a = invocation.getArgument(0);
            AgentVO vo = new AgentVO();
            vo.setId(a.getId());
            vo.setName(a.getName());
            vo.setRole(a.getRole());
            vo.setKey(a.getKey());
            vo.setIsSystem(a.getIsSystem());
            vo.setLlmConfigId(a.getLlmConfigId());
            return vo;
        });
    }

    @Test
    @DisplayName("UT-AGT-001: 创建Agent成功")
    void create_Success() {
        // Given
        AgentRequest request = new AgentRequest();
        request.setName("面试官Agent");
        request.setRole("interviewer");
        request.setSystemPrompt("你是一位专业的面试官");
        request.setType(AgentType.INTERVIEW);

        when(agentMapper.insert(any(Agent.class))).thenReturn(1);

        // When
        AgentVO result = agentService.create(request);

        // Then
        assertNotNull(result);
        assertEquals("面试官Agent", result.getName());
        assertEquals("interviewer", result.getRole());
        assertFalse(result.getIsSystem());
        verify(agentMapper, times(1)).insert(any(Agent.class));
    }

    @Test
    @DisplayName("创建Agent-生成唯一Key")
    void create_GeneratesKey() {
        // Given
        AgentRequest request = new AgentRequest();
        request.setName("测试Agent");

        when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            assertNotNull(agent.getKey());
            assertTrue(agent.getKey().startsWith("agent_"));
            return 1;
        });

        // When
        agentService.create(request);

        // Then
        verify(agentMapper, times(1)).insert(any(Agent.class));
    }

    @Test
    @DisplayName("创建Agent-绑定LLM配置")
    void create_WithLlmConfig() {
        // Given
        AgentRequest request = new AgentRequest();
        request.setName("测试Agent");
        request.setLlmConfigId(1L);

        AgentLlmConfig llmConfig = new AgentLlmConfig();
        llmConfig.setId(1L);
        llmConfig.setName("GPT-4配置");

        when(agentLlmConfigMapper.selectById(1L)).thenReturn(llmConfig);
        when(agentMapper.insert(any(Agent.class))).thenReturn(1);

        // When
        AgentVO result = agentService.create(request);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getLlmConfigId());
    }

    @Test
    @DisplayName("创建Agent-LLM配置不存在")
    void create_LlmConfigNotFound() {
        // Given
        AgentRequest request = new AgentRequest();
        request.setName("测试Agent");
        request.setLlmConfigId(999L);

        when(agentLlmConfigMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> agentService.create(request));
    }

    @Test
    @DisplayName("获取Agent详情成功")
    void getVOById_Success() {
        // Given
        Long agentId = 1L;
        Agent agent = createTestAgent(agentId);
        when(agentMapper.selectById(agentId)).thenReturn(agent);

        // When
        AgentVO result = agentService.getVOById(agentId);

        // Then
        assertNotNull(result);
        assertEquals(agentId, result.getId());
        assertEquals("面试官Agent", result.getName());
    }

    @Test
    @DisplayName("获取Agent详情-Agent不存在")
    void getVOById_NotFound() {
        // Given
        Long agentId = 999L;
        when(agentMapper.selectById(agentId)).thenReturn(null);

        // When & Then
        assertThrows(BusinessException.class, () -> agentService.getVOById(agentId));
    }

    @Test
    @DisplayName("获取Agent详情-无权访问他人Agent")
    void getVOById_Forbidden() {
        // Given
        Long agentId = 1L;
        Agent agent = createTestAgent(agentId);
        agent.setUserId(999L);
        when(agentMapper.selectById(agentId)).thenReturn(agent);

        // When & Then
        assertThrows(BusinessException.class, () -> agentService.getVOById(agentId));
    }

    @Test
    @DisplayName("更新Agent成功")
    void update_Success() {
        // Given
        Long agentId = 1L;
        Agent existing = createTestAgent(agentId);
        when(agentMapper.selectById(agentId)).thenReturn(existing);
        when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

        AgentRequest request = new AgentRequest();
        request.setName("更新后的Agent");
        request.setRole("evaluator");

        // When
        AgentVO result = agentService.update(agentId, request);

        // Then
        assertNotNull(result);
        verify(agentMapper, times(1)).updateById(any(Agent.class));
    }

    @Test
    @DisplayName("更新Agent-系统Agent不可修改")
    void update_SystemAgentForbidden() {
        // Given
        Long agentId = 1L;
        Agent existing = createTestAgent(agentId);
        existing.setIsSystem(true);
        when(agentMapper.selectById(agentId)).thenReturn(existing);

        AgentRequest request = new AgentRequest();
        request.setName("尝试修改系统Agent");

        // When & Then
        assertThrows(BusinessException.class, () -> agentService.update(agentId, request));
    }

    @Test
    @DisplayName("删除Agent成功")
    void delete_Success() {
        // Given
        Long agentId = 1L;
        Agent existing = createTestAgent(agentId);
        when(agentMapper.selectById(agentId)).thenReturn(existing);
        when(agentMapper.deleteById(agentId)).thenReturn(1);

        // When
        agentService.delete(agentId);

        // Then
        verify(agentMapper, times(1)).deleteById(agentId);
    }

    @Test
    @DisplayName("删除Agent-系统Agent不可删除")
    void delete_SystemAgentForbidden() {
        // Given
        Long agentId = 1L;
        Agent existing = createTestAgent(agentId);
        existing.setIsSystem(true);
        when(agentMapper.selectById(agentId)).thenReturn(existing);

        // When & Then
        assertThrows(BusinessException.class, () -> agentService.delete(agentId));
    }

    @Test
    @DisplayName("获取当前用户Agent列表")
    void listByCurrentUser_Success() {
        // Given
        List<Agent> agents = Arrays.asList(
                createTestAgent(1L),
                createTestAgent(2L)
        );
        when(agentMapper.selectList(any())).thenReturn(agents);

        // When
        List<AgentVO> result = agentService.listByCurrentUser();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("按类型查询Agent列表")
    void listByType_Success() {
        // Given
        List<Agent> agents = Arrays.asList(
                createTestAgent(1L)
        );
        when(agentMapper.selectList(any())).thenReturn(agents);

        // When
        List<AgentVO> result = agentService.listByType("INTERVIEW");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    private Agent createTestAgent(Long id) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setUserId(TEST_USER_ID);
        agent.setKey("agent_test1234567890");
        agent.setName("面试官Agent");
        agent.setRole("interviewer");
        agent.setSystemPrompt("你是一位专业的面试官");
        agent.setType(AgentType.INTERVIEW);
        agent.setIsSystem(false);
        return agent;
    }
}
