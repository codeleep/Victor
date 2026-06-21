package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.AgentType;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具模块初始化器
 * 负责创建独立的工具类 Agent（不属于任何团队）
 */
@Slf4j
@Component
@Order(5)
public class UtilityModuleInitializer extends BaseInitializer implements ModuleInitializer {

    public static final String KEY_RESUME_PARSER = "resume-parser";
    public static final String KEY_RESOURCE_RECALL = "resource-recall";

    /** 资料查询工具名(对应 ResourceQueryTool 的 @Tool name) */
    public static final String TOOL_RESOURCE_QUERY = "resource_query";

    private final AgentLlmConfigMapper agentLlmConfigMapper;

    public UtilityModuleInitializer(AgentMapper agentMapper, AgentTeamMapper agentTeamMapper,
                                     AgentLlmConfigMapper agentLlmConfigMapper) {
        super(agentMapper, agentTeamMapper);
        this.agentLlmConfigMapper = agentLlmConfigMapper;
    }

    @Override
    public Map<String, Object> init(Long userId) {
        int agentCreated = 0;
        Long llmConfigId = getDefaultLlmConfigId(userId);

        // 简历解析 Agent
        Object[] r1 = ensureAgent(userId, KEY_RESUME_PARSER, "简历解析Agent",
                "负责将原始简历内容转换为结构化的 Markdown 格式",
                loadPrompt(KEY_RESUME_PARSER), llmConfigId, AgentType.INTERVIEW, List.of());
        if ((boolean) r1[1]) agentCreated++;

        // 资料召回 Agent
        Object[] r2 = ensureAgent(userId, KEY_RESOURCE_RECALL, "资料召回Agent",
                "负责根据面试题目和岗位要求召回相关的参考资料",
                loadPrompt(KEY_RESOURCE_RECALL), llmConfigId, AgentType.INTERVIEW, List.of(TOOL_RESOURCE_QUERY));
        if ((boolean) r2[1]) agentCreated++;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("utilityAgentCreated", agentCreated);
        return result;
    }

    private Long getDefaultLlmConfigId(Long userId) {
        AgentLlmConfig config = agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .last("LIMIT 1"));
        return config != null ? config.getId() : null;
    }
}
