package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.entity.Question;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.infra.agent.core.LlmDefinition;
import me.codeleep.victor.infra.agent.llm.ModelWrapperFactory;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import me.codeleep.victor.core.mapper.QuestionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 驱动的多轮资料召回服务。
 * <p>
 * 流程：
 * <ol>
 *   <li>根据 JD 与简历，调用 LLM 提取与岗位技术栈直接相关的检索关键词</li>
 *   <li>用关键词在题库/经历库中检索候选</li>
 *   <li>调用 LLM 对候选做相关性过滤，剔除与岗位/简历不相关的结果（避免跨领域误命中）</li>
 *   <li>将已召回资料摘要回喂 LLM，提取「尚未覆盖」方向的新关键词</li>
 *   <li>以新关键词再次检索并过滤，循环不超过 {@link #MAX_ROUNDS} 轮</li>
 *   <li>总召回资料不超过 {@link #MAX_TOTAL} 条</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecallService {

    private static final int MAX_ROUNDS = 5;
    private static final int MAX_TOTAL = 50;
    /** 每轮每类资源的查询条数上限 */
    private static final int PER_ROUND_QUERY_LIMIT = 20;
    /** 每轮用于 SQL 检索的关键词上限 */
    private static final int KEYWORDS_PER_ROUND = 12;
    /** 每轮 LLM 提取的关键词数量期望 */
    private static final int EXPECTED_KEYWORD_COUNT = 10;
    /** 单轮送入 LLM 相关性过滤的候选数量上限 */
    private static final int RELEVANCE_FILTER_BATCH = 30;

    /** 过于通用、容易跨领域误命中的停用词（小写） */
    private static final Set<String> STOP_WORDS = Set.of(
            "api", "http", "https", "url", "json", "xml", "sql", "ui", "ux",
            "性能", "优化", "原理", "特点", "区别", "机制", "流程", "设计",
            "基础", "入门", "高级", "常见", "问题", "面试", "项目", "实践",
            "true", "false", "null", "none", "medium", "easy", "hard", "technical"
    );

    private final ModelWrapperFactory modelWrapperFactory;
    private final AgentLlmConfigMapper agentLlmConfigMapper;
    private final QuestionMapper questionMapper;
    private final ExperienceMapper experienceMapper;
    private final ObjectMapper objectMapper;

    /**
     * 执行 AI 多轮召回。
     *
     * @param userId         用户ID
     * @param job            岗位（可为 null）
     * @param resume         简历（可为 null）
     * @param maxRecallCount 召回数量上限（会被 clamp 到 [5, MAX_TOTAL]）
     * @return 召回资料 payload 列表，结构与关键词召回保持一致
     */
    public List<Map<String, Object>> recall(Long userId, Job job, Resume resume, int maxRecallCount) {
        int limit = Math.max(5, Math.min(maxRecallCount, MAX_TOTAL));

        LlmDefinition llm = buildDefaultLlmOrThrow();
        String jobText = formatJobForPrompt(job);
        String resumeText = formatResumeForPrompt(resume);

        // 召回结果：key = type-id，去重
        Map<String, Map<String, Object>> recalled = new LinkedHashMap<>();
        // 全部已使用的关键词（跨轮累积，避免重复提取）
        Set<String> usedKeywords = new LinkedHashSet<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            if (recalled.size() >= limit) {
                break;
            }

            List<String> keywords;
            try {
                if (round == 1) {
                    keywords = extractInitialKeywords(llm, jobText, resumeText);
                } else {
                    keywords = extractFollowupKeywords(llm, jobText, resumeText, usedKeywords, recalled.values(), round);
                }
            } catch (Exception e) {
                log.warn("AI 召回第 {} 轮关键词提取失败，提前结束迭代: {}", round, e.getMessage());
                break;
            }

            // 过滤停用词与已使用关键词，归一化
            List<String> freshKeywords = normalizeKeywords(keywords, usedKeywords);
            if (freshKeywords.isEmpty()) {
                log.info("AI 召回第 {} 轮无新关键词，结束迭代", round);
                break;
            }
            usedKeywords.addAll(freshKeywords);
            log.info("AI 召回第 {} 轮关键词: {}", round, freshKeywords);

            List<String> queryKeywords = freshKeywords.size() > KEYWORDS_PER_ROUND
                    ? freshKeywords.subList(0, KEYWORDS_PER_ROUND) : freshKeywords;

            // 检索题库并做相关性过滤
            List<Question> questionCandidates = searchQuestions(userId, queryKeywords);
            if (!questionCandidates.isEmpty()) {
                Set<Long> relevantIds = filterRelevantQuestions(llm, questionCandidates, jobText, resumeText);
                log.info("AI 召回第 {} 轮题库候选 {} 条，相关性过滤后 {} 条",
                        round, questionCandidates.size(), relevantIds.size());
                for (Question q : questionCandidates) {
                    if (recalled.size() >= limit) {
                        break;
                    }
                    if (relevantIds.contains(q.getId())) {
                        String key = "QUESTION-" + q.getId();
                        if (!recalled.containsKey(key)) {
                            recalled.put(key, toRecallPayload(q, round, freshKeywords));
                        }
                    }
                }
            }

            // 检索经历库并做相关性过滤
            List<Experience> experienceCandidates = searchExperiences(userId, queryKeywords);
            if (!experienceCandidates.isEmpty()) {
                Set<Long> relevantIds = filterRelevantExperiences(llm, experienceCandidates, jobText, resumeText);
                log.info("AI 召回第 {} 轮经历候选 {} 条，相关性过滤后 {} 条",
                        round, experienceCandidates.size(), relevantIds.size());
                for (Experience e : experienceCandidates) {
                    if (recalled.size() >= limit) {
                        break;
                    }
                    if (relevantIds.contains(e.getId())) {
                        String key = "EXPERIENCE-" + e.getId();
                        if (!recalled.containsKey(key)) {
                            recalled.put(key, toRecallPayload(e, round, freshKeywords));
                        }
                    }
                }
            }

            log.info("AI 召回第 {} 轮结束，累计 {} 条", round, recalled.size());
        }

        // 按召回轮次与顺序输出
        List<Map<String, Object>> result = new ArrayList<>(recalled.values());
        int sortOrder = 1;
        for (Map<String, Object> payload : result) {
            payload.put("sortOrder", sortOrder);
            payload.put("sort_order", sortOrder);
            sortOrder++;
        }
        return result;
    }

    // ==================== LLM 关键词提取 ====================

    private List<String> extractInitialKeywords(LlmDefinition llm, String jobText, String resumeText) {
        String prompt = """
                你是一名资深技术面试官。请根据以下岗位描述(JD)和候选人简历，提取用于在题库和经历库中检索相关面试资料的关键词。

                要求：
                1. 关键词必须与岗位和简历中出现的「具体技术栈、框架、工具、业务领域」直接相关
                2. 必须提取岗位方向专有的关键词，例如前端岗位应提取 React/Vue/TypeScript/Webpack 等，后端岗位应提取 Java/Spring/MySQL/Redis 等
                3. 禁止提取过于通用的词，如 "API"、"HTTP"、"性能"、"优化"、"原理"、"机制"、"设计" 等，这些会跨领域误命中
                4. 返回 %d 个左右高质量关键词，优先具体而非宽泛
                5. 仅返回 JSON 字符串数组，不要任何解释，例如：["React","TypeScript","Webpack","Hooks"]

                ## 岗位描述
                %s

                ## 简历
                %s
                """.formatted(EXPECTED_KEYWORD_COUNT, jobText, resumeText);

        String content = modelWrapperFactory.generate(llm, prompt);
        return parseKeywordList(content);
    }

    private List<String> extractFollowupKeywords(LlmDefinition llm, String jobText, String resumeText,
                                                  Set<String> usedKeywords,
                                                  java.util.Collection<Map<String, Object>> recalled,
                                                  int round) {
        String recalledSummary = buildRecalledSummary(recalled);
        String prompt = """
                你正在为一场技术面试迭代召回资料。以下是岗位与简历信息，以及上一轮使用过的关键词和已召回资料列表。
                请判断还有哪些与岗位/简历技术栈直接相关的重要方向尚未被充分覆盖，提取「新的」关键词用于补充检索。

                要求：
                1. 新关键词必须与岗位方向专有的技术栈相关，不要通用词（API/HTTP/性能/原理等）
                2. 只提取尚未覆盖方向的新关键词，不要重复下面「已使用关键词」中已命中过的词
                3. 参考已召回资料，挖掘更深层或关联的技术点（如由 React 延伸到 Hooks/Fiber/并发渲染）
                4. 若认为已召回资料已充分覆盖岗位与简历要点，返回空数组 []
                5. 返回 5-10 个新关键词，仅返回 JSON 字符串数组，例如：["Hooks","虚拟DOM","状态管理"]

                ## 岗位描述
                %s

                ## 简历
                %s

                ## 已使用关键词
                %s

                ## 已召回资料（共 %d 条）
                %s
                """.formatted(jobText, resumeText, String.join("、", usedKeywords), recalled.size(), recalledSummary);

        String content = modelWrapperFactory.generate(llm, prompt);
        return parseKeywordList(content);
    }

    // ==================== LLM 相关性过滤 ====================

    /**
     * 让 LLM 批量判断题目是否与岗位/简历相关，返回相关的题目ID集合。
     * 关键防线：避免宽泛关键词检索到跨领域无关题目（如前端岗位召回到 Java 题）。
     */
    private Set<Long> filterRelevantQuestions(LlmDefinition llm, List<Question> candidates,
                                               String jobText, String resumeText) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        List<Question> batch = candidates.size() > RELEVANCE_FILTER_BATCH
                ? candidates.subList(0, RELEVANCE_FILTER_BATCH) : candidates;

        StringBuilder candidateText = new StringBuilder();
        for (Question q : batch) {
            candidateText.append("ID=").append(q.getId())
                    .append(" | 标题: ").append(nullToEmpty(q.getTitle()))
                    .append(" | 描述: ").append(abbreviate(nullToEmpty(q.getDescription()), 150))
                    .append(" | 参考答案: ").append(abbreviate(nullToEmpty(q.getReferenceAnswer()), 200))
                    .append("\n");
        }

        String prompt = """
                你是一名资深技术面试官。请判断下列题目是否与给定岗位和简历「相关」。
                判断标准：题目考察的核心技术应属于岗位方向的技术栈，或与简历经历直接相关。
                明显属于其他技术领域、与岗位方向无关的题目判为不相关。
                例如：岗位是前端工程师，则 Java/JVM/Spring/MySQL 等后端题目判为不相关。

                ## 岗位描述
                %s

                ## 简历
                %s

                ## 待判断题目
                %s

                请返回相关题目的 ID 数组。若全部不相关，返回 []。
                仅返回 JSON 数字数组，不要任何解释，例如：[14,17,23]
                """.formatted(jobText, resumeText, candidateText);

        return parseIdSet(modelWrapperFactory.generate(llm, prompt));
    }

    private Set<Long> filterRelevantExperiences(LlmDefinition llm, List<Experience> candidates,
                                                  String jobText, String resumeText) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        List<Experience> batch = candidates.size() > RELEVANCE_FILTER_BATCH
                ? candidates.subList(0, RELEVANCE_FILTER_BATCH) : candidates;

        StringBuilder candidateText = new StringBuilder();
        for (Experience e : batch) {
            candidateText.append("ID=").append(e.getId())
                    .append(" | 标题: ").append(nullToEmpty(e.getTitle()))
                    .append(" | 描述: ").append(abbreviate(nullToEmpty(e.getDescription()), 200))
                    .append(" | 技能: ").append(e.getSkills() != null ? e.getSkills() : List.of())
                    .append("\n");
        }

        String prompt = """
                你是一名资深技术面试官。请判断下列经历是否与给定岗位和简历「相关」。
                判断标准：经历涉及的技术栈应属于岗位方向，或与候选人简历经历可关联对比。
                与岗位方向完全无关的经历判为不相关。

                ## 岗位描述
                %s

                ## 简历
                %s

                ## 待判断经历
                %s

                请返回相关经历的 ID 数组。若全部不相关，返回 []。
                仅返回 JSON 数字数组，不要任何解释，例如：[3,5]
                """.formatted(jobText, resumeText, candidateText);

        return parseIdSet(modelWrapperFactory.generate(llm, prompt));
    }

    private Set<Long> parseIdSet(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        String json = extractJsonArray(content);
        if (json == null) {
            return Set.of();
        }
        try {
            List<Integer> ids = objectMapper.readValue(json, new TypeReference<>() {});
            Set<Long> result = new LinkedHashSet<>();
            for (Integer id : ids) {
                if (id != null) {
                    result.add(id.longValue());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 LLM 相关性 ID 数组失败: {}, content={}", e.getMessage(), abbreviate(content, 200));
            return Set.of();
        }
    }

    // ==================== 解析辅助 ====================

    private List<String> parseKeywordList(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String json = extractJsonArray(content);
        if (json == null) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<String> result = new ArrayList<>();
            for (String kw : raw) {
                if (kw != null) {
                    String trimmed = kw.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 LLM 关键词 JSON 失败: {}, content={}", e.getMessage(), abbreviate(content, 200));
            return List.of();
        }
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf("[");
        int end = content.lastIndexOf("]");
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    // ==================== 资源检索 ====================

    private List<Question> searchQuestions(Long userId, List<String> keywords) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getUserId, userId);
        wrapper.and(w -> w.eq(Question::getIngestStatus, IngestStatus.ACTIVE).or().isNull(Question::getIngestStatus));
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (i > 0) {
                    w.or();
                }
                w.like(Question::getTitle, kw)
                        .or().like(Question::getDescription, kw)
                        .or().like(Question::getReferenceAnswer, kw);
            }
        });
        wrapper.orderByDesc(Question::getUpdatedAt);
        wrapper.last("LIMIT " + PER_ROUND_QUERY_LIMIT);
        return questionMapper.selectList(wrapper);
    }

    private List<Experience> searchExperiences(Long userId, List<String> keywords) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experience::getUserId, userId);
        wrapper.and(w -> w.eq(Experience::getIngestStatus, IngestStatus.ACTIVE).or().isNull(Experience::getIngestStatus));
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (i > 0) {
                    w.or();
                }
                w.like(Experience::getTitle, kw)
                        .or().like(Experience::getDescription, kw);
            }
        });
        wrapper.orderByDesc(Experience::getUpdatedAt);
        wrapper.last("LIMIT " + PER_ROUND_QUERY_LIMIT);
        return experienceMapper.selectList(wrapper);
    }

    // ==================== 辅助 ====================

    private LlmDefinition buildDefaultLlmOrThrow() {
        AgentLlmConfig llmConfig = agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .eq(AgentLlmConfig::getIsEnabled, true)
        );
        if (llmConfig == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未配置默认LLM，无法执行AI召回");
        }
        String apiKey = llmConfig.getAuthParams() != null
                ? Objects.toString(llmConfig.getAuthParams().getOrDefault("apiKey", ""), "")
                : "";
        return LlmDefinition.builder()
                .protocol(llmConfig.getProtocol())
                .baseUrl(llmConfig.getApiEndpoint())
                .apiKey(apiKey)
                .modelName(llmConfig.getModelName())
                .temperature(llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.3)
                .maxTokens(llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 2048)
                .build();
    }

    private List<String> normalizeKeywords(List<String> keywords, Set<String> usedKeywords) {
        List<String> fresh = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String kw : keywords) {
            if (kw == null) {
                continue;
            }
            String normalized = kw.trim().toLowerCase(Locale.ROOT);
            // 过滤停用词、过短词、已使用词、本轮重复词
            if (normalized.length() < 2 || STOP_WORDS.contains(normalized)
                    || usedKeywords.contains(normalized) || seen.contains(normalized)) {
                continue;
            }
            seen.add(normalized);
            fresh.add(kw.trim());
        }
        return fresh;
    }

    private String buildRecalledSummary(java.util.Collection<Map<String, Object>> recalled) {
        if (recalled.isEmpty()) {
            return "（暂无）";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (Map<String, Object> item : recalled) {
            String type = Objects.toString(item.get("type"), "");
            String label = "EXPERIENCE".equals(type) ? "经历" : "题目";
            String title = abbreviate(Objects.toString(item.get("title"), ""), 50);
            sb.append(idx++).append(". [").append(label).append("] ").append(title).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatJobForPrompt(Job job) {
        if (job == null) {
            return "未选择岗位";
        }
        return "岗位名称: " + nullToEmpty(job.getName()) + "\n"
                + "岗位描述: " + nullToEmpty(job.getDescription()) + "\n"
                + "技能要求: " + (job.getRequiredSkills() != null ? job.getRequiredSkills() : List.of()) + "\n"
                + "领域: " + (job.getDomains() != null ? job.getDomains() : List.of());
    }

    private String formatResumeForPrompt(Resume resume) {
        if (resume == null) {
            return "未选择简历";
        }
        return "简历名称: " + nullToEmpty(resume.getName()) + "\n"
                + "简历摘要: " + (resume.getSummary() != null ? resume.getSummary() : Map.of()) + "\n"
                + "结构化内容: " + abbreviate(String.valueOf(resume.getParsedContent()), 1500) + "\n"
                + "原文片段: " + abbreviate(nullToEmpty(resume.getRawText()), 2000);
    }

    private Map<String, Object> toRecallPayload(Question question, int round, List<String> matchedKeywords) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", question.getId());
        payload.put("type", "QUESTION");
        payload.put("sourceType", "QUESTION");
        payload.put("source_type", "QUESTION");
        payload.put("sourceId", question.getId());
        payload.put("source_id", question.getId());
        payload.put("title", Objects.toString(question.getTitle(), "Untitled question"));
        payload.put("recallMethod", "AI");
        payload.put("recall_method", "AI");
        payload.put("recallScore", roundScore(round));
        payload.put("recall_score", roundScore(round));
        payload.put("recallRound", round);
        payload.put("matchedKeywords", matchedKeywords);
        payload.put("reason", "AI第" + round + "轮召回，匹配关键词: " + String.join("、", matchedKeywords));
        return payload;
    }

    private Map<String, Object> toRecallPayload(Experience experience, int round, List<String> matchedKeywords) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", experience.getId());
        payload.put("type", "EXPERIENCE");
        payload.put("sourceType", "EXPERIENCE");
        payload.put("source_type", "EXPERIENCE");
        payload.put("sourceId", experience.getId());
        payload.put("source_id", experience.getId());
        payload.put("title", Objects.toString(experience.getTitle(), "Untitled experience"));
        payload.put("recallMethod", "AI");
        payload.put("recall_method", "AI");
        payload.put("recallScore", roundScore(round));
        payload.put("recall_score", roundScore(round));
        payload.put("recallRound", round);
        payload.put("matchedKeywords", matchedKeywords);
        payload.put("reason", "AI第" + round + "轮召回，匹配关键词: " + String.join("、", matchedKeywords));
        return payload;
    }

    private double roundScore(int round) {
        return Math.round(Math.max(0.3, 1.0 - (round - 1) * 0.15) * 10000.0) / 10000.0;
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
}