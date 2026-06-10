package me.codeleep.victor.web.tool;

import me.codeleep.victor.common.enums.*;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.entity.*;
import me.codeleep.victor.core.mapper.*;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import me.codeleep.victor.web.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 相关数据导入脚本（LLM配置、Agent、Agent团队）
 * 由于 BaseApiTest 有 @Transactional，所有操作放在同一个方法中以保证数据一致性
 */
class AgentImportTest extends BaseApiTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private AgentLlmConfigMapper agentLlmConfigMapper;
    @Autowired
    private AgentMapper agentMapper;
    @Autowired
    private AgentTeamMapper agentTeamMapper;

    @Test
    @DisplayName("导入LLM配置、Agent和Agent团队")
    @Rollback(false)
    void importAll() {
        // ==================== 1. LLM 配置 ====================
        AgentLlmConfig config = new AgentLlmConfig();
        config.setUserId(USER_ID);
        config.setName("火山方舟推理模型");
        config.setDescription("火山方舟 Ark 编程推理模型");
        config.setProvider("DOUBAO");
        config.setApiEndpoint(env("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/coding/v3"));
        config.setAuthParams(Map.of("apiKey", env("ARK_API_KEY", "")));
        config.setProtocol(LlmProtocol.DOUBAO);
        config.setModelName(env("ARK_MODEL", "ark-code-latest"));
        config.setModelType(ModelType.INFERENCE);
        config.setTemperature(new BigDecimal("0.70"));
        config.setMaxTokens(4096);
        config.setIsEnabled(true);
        config.setIsDefault(true);
        agentLlmConfigMapper.insert(config);
        Long llmConfigId = config.getId();
        System.out.println("[成功] LLM配置 id=" + llmConfigId + " " + config.getName());

        // ==================== 2. Agents ====================
        // 2.1 出题 Agent
        Agent q = newAgent("user-interview-question", "出题Agent", "负责根据岗位要求和候选人简历生成面试题目",
                "你是一个专业的面试出题专家。根据面试配置、岗位要求和候选人简历，生成有针对性的面试题目。你需要：\n" +
                        "1. 根据轮次主题生成相应领域的题目\n" +
                        "2. 题目难度需要匹配配置中的难度要求\n" +
                        "3. 结合候选人简历中的经验进行针对性提问\n" +
                        "4. 避免重复出题\n" +
                        "5. 每道题应该有明确的考察点",
                llmConfigId, List.of("search", "question_query", "resume_query"), AgentType.INTERVIEW);
        agentMapper.insert(q);
        System.out.println("[成功] Agent id=" + q.getId() + " " + q.getName());

        // 2.2 追问 Agent
        Agent f = newAgent("user-interview-followup", "追问Agent", "负责根据候选人的回答生成追问",
                "你是一个面试追问专家。根据候选人的回答，判断是否需要深入追问。你需要：\n" +
                        "1. 评估候选人回答的完整性和深度\n" +
                        "2. 如果回答不够深入，生成追问\n" +
                        "3. 追问应该围绕原始题目展开\n" +
                        "4. 最多追问2轮\n" +
                        "5. 如果回答已经充分，建议进入下一题",
                llmConfigId, List.of("search"), AgentType.INTERVIEW);
        agentMapper.insert(f);
        System.out.println("[成功] Agent id=" + f.getId() + " " + f.getName());

        // 2.3 技术评估 Agent
        Agent e = newAgent("user-evaluation-technical", "技术评估Agent", "评估技术能力和代码质量",
                "你是一个技术能力评估专家。根据面试对话记录，评估候选人的技术能力。你需要：\n" +
                        "1. 评估编程语言和框架的掌握程度\n" +
                        "2. 评估系统设计和架构能力\n" +
                        "3. 评估代码质量和工程实践\n" +
                        "4. 给出具体的技术维度评分和评语\n" +
                        "5. 引用面试中的具体回答作为证据",
                llmConfigId, List.of("search", "question_query"), AgentType.EVALUATION);
        agentMapper.insert(e);
        System.out.println("[成功] Agent id=" + e.getId() + " " + e.getName());

        // 2.4 知识检索 Agent
        Agent s = newAgent("user-search-knowledge", "知识检索Agent", "检索知识库和简历信息",
                "你是知识检索专家，负责从知识库和简历中检索相关信息。你需要：\n" +
                        "1. 根据查询关键词进行向量检索\n" +
                        "2. 从多个数据源（题库、文档、简历、经历）检索\n" +
                        "3. 对检索结果进行相关性排序\n" +
                        "4. 融合多个检索结果\n" +
                        "5. 返回最相关的Top-K结果",
                llmConfigId, List.of("search", "question_query", "document_query", "resume_query"), AgentType.SEARCH);
        agentMapper.insert(s);
        System.out.println("[成功] Agent id=" + s.getId() + " " + s.getName());

        System.out.println("=== Agent导入完成: 共 4 个 ===");

        // ==================== 3. Agent 团队 ====================
        Map<String, Agent> agentMap = Map.of(
                q.getKey(), q, f.getKey(), f, e.getKey(), e, s.getKey(), s
        );

        // 3.1 面试团队（串行）
        AgentTeam interviewTeam = new AgentTeam();
        interviewTeam.setUserId(USER_ID);
        interviewTeam.setKey("user-team-interview");
        interviewTeam.setName("面试团队");
        interviewTeam.setDescription("负责面试出题、追问和知识检索");
        interviewTeam.setExecutionMode(TeamExecutionMode.SEQUENTIAL);
        interviewTeam.setIsSystem(false);
        interviewTeam.setMembers(List.of(
                member(agentMap, "user-interview-question", "出题", 1),
                member(agentMap, "user-interview-followup", "追问", 2),
                member(agentMap, "user-search-knowledge", "检索", 3)
        ));
        agentTeamMapper.insert(interviewTeam);
        System.out.println("[成功] 团队 id=" + interviewTeam.getId() + " " + interviewTeam.getName());

        // 3.2 评估团队（并行）
        AgentTeam evalTeam = new AgentTeam();
        evalTeam.setUserId(USER_ID);
        evalTeam.setKey("user-team-evaluation");
        evalTeam.setName("评估团队");
        evalTeam.setDescription("负责多维度评估面试表现");
        evalTeam.setExecutionMode(TeamExecutionMode.PARALLEL);
        evalTeam.setIsSystem(false);
        evalTeam.setMembers(List.of(
                member(agentMap, "user-evaluation-technical", "技术评估", 1)
        ));
        agentTeamMapper.insert(evalTeam);
        System.out.println("[成功] 团队 id=" + evalTeam.getId() + " " + evalTeam.getName());

        System.out.println("=== Agent团队导入完成: 共 2 个 ===");
        System.out.println("========================================");
        System.out.println("全部导入完成! LLM配置=" + llmConfigId + ", Agents=4, Teams=2");
    }

    // ==================== 工具方法 ====================

    private Agent newAgent(String key, String name, String role, String prompt,
                           Long llmConfigId, List<String> tools, AgentType type) {
        Agent a = new Agent();
        a.setUserId(USER_ID);
        a.setKey(key);
        a.setName(name);
        a.setRole(role);
        a.setSystemPrompt(prompt);
        a.setLlmConfigId(llmConfigId);
        a.setAvailableTools(tools);
        a.setType(type);
        a.setIsSystem(false);
        return a;
    }

    private TeamMemberInfo member(Map<String, Agent> map, String key, String role, int priority) {
        Agent a = map.get(key);
        return new TeamMemberInfo(a.getId(), a.getKey(), a.getName(), role, priority);
    }

    private String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }
}
