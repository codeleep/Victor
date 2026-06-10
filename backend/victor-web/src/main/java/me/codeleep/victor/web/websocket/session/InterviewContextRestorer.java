package me.codeleep.victor.web.websocket.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.Speaker;
import me.codeleep.victor.common.utils.SafeGet;
import me.codeleep.victor.core.dto.TeamMemberInfo;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.InterviewTurn;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.AgentMapper;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.InterviewQuestionMapper;
import me.codeleep.victor.core.mapper.InterviewTurnMapper;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.web.websocket.processor.ProcessingContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class InterviewContextRestorer {

    private static final String DEFAULT_AGENT_KEY = "interviewer-main";

    private final InterviewConfigMapper configMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewTurnMapper turnMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final AgentMapper agentMapper;
    private final AgentLlmConfigMapper agentLlmConfigMapper;
    private final AgentTeamMapper agentTeamMapper;

    public InterviewContextRestorer(InterviewConfigMapper configMapper,
                                    InterviewQuestionMapper questionMapper,
                                    InterviewTurnMapper turnMapper,
                                    JobMapper jobMapper,
                                    ResumeMapper resumeMapper,
                                    AgentMapper agentMapper,
                                    AgentLlmConfigMapper agentLlmConfigMapper,
                                    AgentTeamMapper agentTeamMapper) {
        this.configMapper = configMapper;
        this.questionMapper = questionMapper;
        this.turnMapper = turnMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.agentMapper = agentMapper;
        this.agentLlmConfigMapper = agentLlmConfigMapper;
        this.agentTeamMapper = agentTeamMapper;
    }

    public long ensureContext(Long interviewSessionId, ProcessingContext context) {
        if (interviewSessionId == null) {
            log.warn("[Restorer] InterviewSessionId is null");
            return -1;
        }

        InterviewConfig config = configMapper.selectById(interviewSessionId);
        if (config == null) {
            log.warn("[Restorer] Interview config not found: {}", interviewSessionId);
            return -1;
        }
        if (config.getStatus() == InterviewConfigStatus.COMPLETED || config.getStatus() == InterviewConfigStatus.ABANDONED) {
            log.warn("[Restorer] Interview config status not recoverable: {}, status={}", interviewSessionId, config.getStatus());
            return -2;
        }

        if (!isRunnableStatus(config.getStatus())) {
            log.warn("[Restorer] Interview config is not runnable: configId={}, status={}",
                    interviewSessionId, config.getStatus());
            return -4;
        }

        InterviewQuestion currentQuestion = resolveCurrentQuestion(config);
        if (currentQuestion == null) {
            log.warn("[Restorer] No generated interview question found: configId={}", interviewSessionId);
            return -4;
        }

        // 查找面试官Agent：优先从团队成员中查找，其次按key查找
        Agent interviewerAgent = resolveInterviewerAgent(config);
        if (interviewerAgent == null) {
            log.error("[Restorer] 面试官Agent未找到: userId={}, teamConfig={}", config.getUserId(), config.getTeamConfig());
            return -3;
        }

        AgentDefinition agentDef = buildAgentDefinition(interviewerAgent);
        if (agentDef == null) {
            log.error("[Restorer] Agent LLM配置无效: agentId={}", interviewerAgent.getId());
            return -3;
        }

        AgentContext agentContext = new AgentContext(String.valueOf(interviewSessionId), config.getUserId());
        agentContext.setVariable("configId", config.getId());
        agentContext.setVariable("currentQuestionId", currentQuestion.getId());
        agentContext.setVariable("currentQuestion", currentQuestion.getQuestionText());

        // 运行时上下文作为 user message
        agentContext.addUserMessage(buildRuntimeUserMessage(config, currentQuestion));

        List<InterviewTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<InterviewTurn>()
                        .eq(InterviewTurn::getSessionId, interviewSessionId)
                        .orderByAsc(InterviewTurn::getTurnIndex)
        );
        for (InterviewTurn turn : turns) {
            String role = mapSpeakerToRole(turn.getSpeaker());
            if (role != null) {
                agentContext.getConversationHistory().add(new AgentContext.ChatMessage(role, turn.getContent()));
            }
        }

        context.setAttribute(ProcessingContext.ATTR_AGENT_DEFINITION, agentDef);
        context.setAttribute(ProcessingContext.ATTR_AGENT_KEY, interviewerAgent.getKey());
        context.setAttribute(ProcessingContext.ATTR_AGENT_CONTEXT, agentContext);
        context.setAttribute(ProcessingContext.ATTR_USER_ID, config.getUserId());
        context.setAttribute(ProcessingContext.ATTR_INTERVIEW_SESSION_ID, interviewSessionId);
        context.setAttribute(ProcessingContext.ATTR_CURRENT_QUESTION_ID, currentQuestion.getId());

        if (config.getStatus() == InterviewConfigStatus.READY || config.getStatus() == InterviewConfigStatus.PAUSED) {
            config.setStatus(InterviewConfigStatus.IN_PROGRESS);
            if (config.getStartedAt() == null) {
                config.setStartedAt(LocalDateTime.now());
            }
            configMapper.updateById(config);
            log.info("[Restorer] Config status updated to IN_PROGRESS: {}", interviewSessionId);
        }

        int turnCount = agentContext.getConversationHistory().size();
        log.info("[Restorer] Context ready: session={}, agentKey={}, questionId={}, turns={}",
                interviewSessionId, interviewerAgent.getKey(), currentQuestion.getId(), turnCount);
        return turnCount;
    }

    private InterviewQuestion resolveCurrentQuestion(InterviewConfig config) {
        InterviewQuestion question = null;
        if (config.getCurrentQuestionId() != null) {
            question = questionMapper.selectById(config.getCurrentQuestionId());
        }
        if (question == null) {
            question = questionMapper.selectOne(
                    new LambdaQueryWrapper<InterviewQuestion>()
                            .eq(InterviewQuestion::getConfigId, config.getId())
                            .orderByAsc(InterviewQuestion::getOrderIndex)
                            .last("LIMIT 1")
            );
        }
        if (question != null && !question.getId().equals(config.getCurrentQuestionId())) {
            InterviewConfig update = new InterviewConfig();
            update.setId(config.getId());
            update.setCurrentQuestionId(question.getId());
            configMapper.updateById(update);
            config.setCurrentQuestionId(question.getId());
        }
        return question;
    }

    private boolean isRunnableStatus(InterviewConfigStatus status) {
        return status == InterviewConfigStatus.READY
                || status == InterviewConfigStatus.IN_PROGRESS
                || status == InterviewConfigStatus.PAUSED;
    }

    private String buildRuntimeUserMessage(InterviewConfig config, InterviewQuestion currentQuestion) {
        Job job = config.getJobId() != null ? jobMapper.selectById(config.getJobId()) : null;
        Resume resume = config.getResumeId() != null ? resumeMapper.selectById(config.getResumeId()) : null;

        return """
                请执行本次面试。以下是当前面试的上下文信息。

                ## 当前题目
                题目ID: %d
                题干: %s
                答案要点: %s
                召回引用: %s

                ## 面试配置
                轮次: %s
                难度: %s
                时长: %s 分钟
                召回资料: %s

                ## 岗位
                %s

                ## 简历
                %s
                """.formatted(
                currentQuestion.getId(),
                currentQuestion.getQuestionText(),
                currentQuestion.getAnswerHint() != null ? currentQuestion.getAnswerHint() : Map.of(),
                currentQuestion.getSourceRecallRefs() != null ? currentQuestion.getSourceRecallRefs() : List.of(),
                config.getRounds() != null ? config.getRounds() : List.of(),
                config.getDifficultyConfig() != null ? config.getDifficultyConfig() : Map.of(),
                config.getDurationMinutes() != null ? config.getDurationMinutes() : "",
                config.getRecallItems() != null ? config.getRecallItems() : List.of(),
                formatJob(job),
                formatResume(resume)
        );
    }

    /**
     * 解析面试官Agent：优先从团队成员中查找，其次按key查找
     */
    private Agent resolveInterviewerAgent(InterviewConfig config) {
        // 1. 从面试团队成员中查找（mainAgentId 优先）
        List<String> teamConfig = config.getTeamConfig();
        String interviewKey = "system-team-interview";
        String matchedKey = null;
        if (teamConfig != null) {
            for (String key : teamConfig) {
                if (interviewKey.equals(key)) {
                    matchedKey = key;
                    break;
                }
            }
        }
        if (matchedKey == null) {
            matchedKey = interviewKey;
        }

        AgentTeam team = agentTeamMapper.selectOne(
                new LambdaQueryWrapper<AgentTeam>()
                        .eq(AgentTeam::getUserId, config.getUserId())
                        .eq(AgentTeam::getKey, matchedKey)
                        .last("LIMIT 1")
        );
        if (team != null) {
            if (team.getMainAgentId() != null) {
                Agent mainAgent = agentMapper.selectById(team.getMainAgentId());
                if (mainAgent != null) return mainAgent;
            }
            if (team.getMembers() != null) {
                for (TeamMemberInfo member : team.getMembers()) {
                    String role = member.getRole();
                    if ("interviewer".equals(role) || "INTERVIEWER".equals(role)) {
                        if (member.getAgentId() != null) {
                            Agent agent = agentMapper.selectById(member.getAgentId());
                            if (agent != null) return agent;
                        }
                    }
                }
            }
        }

        // 2. 按 key 从数据库查找
        Agent dbAgent = agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, config.getUserId())
                        .eq(Agent::getKey, DEFAULT_AGENT_KEY)
                        .last("LIMIT 1")
        );
        if (dbAgent != null) {
            return dbAgent;
        }

        return null;
    }

    private AgentDefinition buildAgentDefinition(Agent agent) {
        if (agent.getLlmConfigId() == null) {
            return null;
        }

        AgentLlmConfig llmConfig = agentLlmConfigMapper.selectById(agent.getLlmConfigId());
        if (llmConfig == null || !Boolean.TRUE.equals(llmConfig.getIsEnabled())) {
            log.warn("[Restorer] LLM config not found or disabled: configId={}", agent.getLlmConfigId());
            return null;
        }

        return AgentDefinition.builder()
                .name(agent.getName())
                .instructions(agent.getSystemPrompt())
                .llmProtocol(llmConfig.getProtocol())
                .llmBaseUrl(llmConfig.getApiEndpoint())
                .llmApiKey(SafeGet.get(() -> llmConfig.getAuthParams().get("apiKey").toString(), ""))
                .modelName(llmConfig.getModelName())
                .temperature(llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.7)
                .maxTokens(llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 4096)
                .toolEnabled(false)
                .build();
    }

    private String formatJob(Job job) {
        if (job == null) {
            return "未选择岗位";
        }
        return "岗位名称: " + job.getName() + "\n"
                + "岗位描述: " + nullToEmpty(job.getDescription()) + "\n"
                + "技能要求: " + (job.getRequiredSkills() != null ? job.getRequiredSkills() : List.of()) + "\n"
                + "领域: " + (job.getDomains() != null ? job.getDomains() : List.of());
    }

    private String formatResume(Resume resume) {
        if (resume == null) {
            return "未选择简历";
        }
        return "简历名称: " + resume.getName() + "\n"
                + "简历摘要: " + (resume.getSummary() != null ? resume.getSummary() : Map.of()) + "\n"
                + "结构化内容: " + (resume.getParsedContent() != null ? resume.getParsedContent() : Map.of()) + "\n"
                + "原文片段: " + abbreviate(resume.getRawText(), 2000);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String mapSpeakerToRole(Speaker speaker) {
        if (speaker == null) return null;
        return switch (speaker) {
            case USER, CANDIDATE -> "user";
            case AI, INTERVIEWER -> "assistant";
        };
    }
}
