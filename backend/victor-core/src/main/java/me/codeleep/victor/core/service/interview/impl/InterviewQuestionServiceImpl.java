package me.codeleep.victor.core.service.interview.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.common.enums.RecallStrategy;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.core.entity.AgentTeam;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.entity.Question;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.AgentTeamMapper;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import me.codeleep.victor.core.mapper.InterviewConfigMapper;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.mapper.QuestionMapper;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.core.service.converter.InterviewConfigConverter;
import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;
import me.codeleep.victor.core.service.dto.TeamAssignment;
import me.codeleep.victor.core.service.interview.InterviewQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 面试出题服务实现(出题线)。
 * <p>
 * 负责面试配置的 CRUD、发布(触发异步出题)、归档与召回预览。
 * 读取配置时对卡在 GENERATING 的出题任务做自愈重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    private final InterviewConfigMapper interviewConfigMapper;
    private final QuestionMapper questionMapper;
    private final ExperienceMapper experienceMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final AgentTeamMapper agentTeamMapper;
    private final InterviewConfigConverter configConverter;
    private final QuestionGenerationExecutor questionGenerationService;

    @Override
    @Transactional
    public Long createConfig(InterviewConfigRequest request) {
        InterviewConfig config = configConverter.toEntity(request);
        config.setUserId(UserContext.getUserId());
        config.setRecallStrategy(request.getRecallStrategy() != null ? request.getRecallStrategy() : RecallStrategy.HYBRID);
        config.setMaxRecallCount(request.getMaxRecallCount() != null ? request.getMaxRecallCount() : 50);
        config.setStatus(InterviewConfigStatus.DRAFT);
        config.setTeamConfig(request.getTeamConfig());
        interviewConfigMapper.insert(config);
        log.info("Interview config created: id={}, name={}", config.getId(), config.getName());
        return config.getId();
    }

    @Override
    @Transactional
    public void updateConfig(Long id, InterviewConfigRequest request) {
        InterviewConfig config = getConfigEntityOrThrow(id);
        configConverter.updateEntity(request, config);
        interviewConfigMapper.updateById(config);
        log.info("Interview config updated: id={}", id);
    }

    @Override
    public InterviewConfigVO getConfig(Long id) {
        InterviewConfig config = interviewConfigMapper.selectById(id);
        if (config == null) {
            return null;
        }
        // 状态自愈: 出题卡在 GENERATING 但无在途任务时重新触发
        if (config.getStatus() == InterviewConfigStatus.GENERATING) {
            questionGenerationService.resumeIfStuck(id);
        }
        return enrichConfigVO(configConverter.toVO(config), config);
    }

    @Override
    public List<InterviewConfigVO> listConfigs() {
        Long userId = UserContext.getUserId();
        List<InterviewConfig> configs = interviewConfigMapper.selectList(
                new LambdaQueryWrapper<InterviewConfig>()
                        .eq(InterviewConfig::getUserId, userId)
                        .orderByDesc(InterviewConfig::getCreatedAt)
        );
        // 状态自愈: 批量恢复卡在 GENERATING 的出题任务
        configs.stream()
                .filter(config -> config.getStatus() == InterviewConfigStatus.GENERATING)
                .forEach(config -> questionGenerationService.resumeIfStuck(config.getId()));
        return configs.stream()
                .map(config -> enrichConfigVO(configConverter.toVO(config), config))
                .toList();
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        interviewConfigMapper.deleteById(id);
        log.info("Interview config deleted: id={}", id);
    }

    @Override
    @Transactional
    public void publishConfig(Long id) {
        InterviewConfig config = getConfigEntityOrThrow(id);
        if (config.getStatus() != InterviewConfigStatus.DRAFT && config.getStatus() != InterviewConfigStatus.GENERATE_FAILED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Current config status cannot be published");
        }

        config.setStatus(InterviewConfigStatus.GENERATING);
        config.setGenerateError(null);
        interviewConfigMapper.updateById(config);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    questionGenerationService.generateAsync(id);
                }
            });
        } else {
            questionGenerationService.generateAsync(id);
        }
        log.info("Interview config submitted for async question generation: id={}", id);
    }

    @Override
    @Transactional
    public void archiveConfig(Long id) {
        InterviewConfig config = getConfigEntityOrThrow(id);
        if (config.getStatus() != InterviewConfigStatus.READY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Only READY config can be archived");
        }
        config.setStatus(InterviewConfigStatus.ARCHIVED);
        interviewConfigMapper.updateById(config);
        log.info("Interview config archived: id={}", id);
    }

    @Override
    public List<Map<String, Object>> previewRecallItems(InterviewConfigRequest request) {
        Long userId = UserContext.getUserId();
        int limit = normalizeRecallLimit(request.getMaxRecallCount());
        Set<String> keywords = buildRecallKeywords(request);

        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getUserId, userId)
                        .and(w -> w.eq(Question::getIngestStatus, IngestStatus.ACTIVE).or().isNull(Question::getIngestStatus))
                        .orderByDesc(Question::getUpdatedAt)
                        .last("LIMIT 200")
        );
        List<Experience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<Experience>()
                        .eq(Experience::getUserId, userId)
                        .and(w -> w.eq(Experience::getIngestStatus, IngestStatus.ACTIVE).or().isNull(Experience::getIngestStatus))
                        .orderByDesc(Experience::getUpdatedAt)
                        .last("LIMIT 200")
        );

        List<ScoredRecallItem> candidates = new ArrayList<>();
        int order = 0;
        for (Question question : questions) {
            double score = scoreText(keywords, joinText(
                    question.getTitle(),
                    question.getDescription(),
                    question.getReferenceAnswer(),
                    question.getType(),
                    question.getDifficulty(),
                    question.getTags()
            ));
            candidates.add(new ScoredRecallItem(toRecallPayload(question, score), score, order++));
        }
        for (Experience experience : experiences) {
            double score = scoreText(keywords, joinText(
                    experience.getTitle(),
                    experience.getType(),
                    experience.getDescription(),
                    experience.getSkills()
            ));
            candidates.add(new ScoredRecallItem(toRecallPayload(experience, score), score, order++));
        }

        candidates.sort(Comparator
                .comparingDouble(ScoredRecallItem::score).reversed()
                .thenComparingInt(ScoredRecallItem::sourceOrder));

        List<Map<String, Object>> result = new ArrayList<>();
        int sortOrder = 1;
        for (ScoredRecallItem candidate : candidates) {
            if (result.size() >= limit) {
                break;
            }
            Map<String, Object> payload = new LinkedHashMap<>(candidate.payload());
            payload.put("sortOrder", sortOrder);
            payload.put("sort_order", sortOrder);
            result.add(payload);
            sortOrder++;
        }
        return result;
    }

    // ==================== 内部辅助 ====================

    private InterviewConfig getConfigEntityOrThrow(Long id) {
        InterviewConfig config = interviewConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Interview config not found");
        }
        return config;
    }

    private InterviewConfigVO enrichConfigVO(InterviewConfigVO vo, InterviewConfig config) {
        if (config.getJobId() != null) {
            Job job = jobMapper.selectById(config.getJobId());
            if (job != null) {
                vo.setJobName(job.getName());
            }
        }
        if (config.getResumeId() != null) {
            Resume resume = resumeMapper.selectById(config.getResumeId());
            if (resume != null) {
                vo.setResumeName(resume.getName());
            }
        }
        vo.setTeamConfig(toTeamAssignmentList(config.getTeamConfig()));
        return vo;
    }

    /**
     * 将实体的 teamConfig（List<String> team keys）转为 VO 的 List<TeamAssignment>，填充团队名称
     */
    private List<TeamAssignment> toTeamAssignmentList(List<String> teamKeys) {
        if (teamKeys == null || teamKeys.isEmpty()) {
            return List.of();
        }
        List<TeamAssignment> result = new ArrayList<>();
        for (String key : teamKeys) {
            if (key == null) continue;
            AgentTeam team = agentTeamMapper.selectOne(
                    new LambdaQueryWrapper<AgentTeam>()
                            .eq(AgentTeam::getUserId, UserContext.getUserId())
                            .eq(AgentTeam::getKey, key)
                            .last("LIMIT 1")
            );
            result.add(new TeamAssignment(key, team != null ? team.getId() : null, team != null ? team.getName() : null));
        }
        return result;
    }

    private int normalizeRecallLimit(Integer maxRecallCount) {
        int limit = maxRecallCount == null ? 20 : maxRecallCount;
        return Math.max(5, Math.min(limit, 100));
    }

    private Set<String> buildRecallKeywords(InterviewConfigRequest request) {
        Set<String> keywords = new LinkedHashSet<>();
        if (request.getJobId() != null) {
            Job job = jobMapper.selectById(request.getJobId());
            if (job != null) {
                addKeywords(keywords, job.getName(), job.getDescription(), job.getRequiredSkills(), job.getDomains());
            }
        }
        if (request.getResumeId() != null) {
            Resume resume = resumeMapper.selectById(request.getResumeId());
            if (resume != null) {
                addKeywords(keywords, resume.getName(), resume.getRawText(), resume.getParsedContent(), resume.getSummary());
            }
        }
        if (request.getRounds() != null) {
            for (Map<String, Object> round : request.getRounds()) {
                addKeywords(keywords, round.values());
            }
        }
        return keywords;
    }

    private void addKeywords(Set<String> keywords, Object... values) {
        for (Object value : values) {
            addKeywords(keywords, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void addKeywords(Set<String> keywords, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> addKeywords(keywords, v));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(v -> addKeywords(keywords, v));
            return;
        }
        String text = String.valueOf(value);
        if (text.length() > 4000) {
            text = text.substring(0, 4000);
        }
        for (String token : text.split("[^\\p{IsHan}A-Za-z0-9+#.]+")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2 && !isWeakRecallToken(normalized)) {
                keywords.add(normalized);
            }
        }
    }

    private boolean isWeakRecallToken(String token) {
        return Set.of("true", "false", "null", "none", "medium", "easy", "hard", "technical").contains(token);
    }

    private String joinText(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (value != null) {
                builder.append(' ').append(value);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private double scoreText(Set<String> keywords, String text) {
        if (keywords.isEmpty() || text == null || text.isBlank()) {
            return 0;
        }
        int matched = 0;
        int hits = 0;
        for (String keyword : keywords) {
            int count = countOccurrences(text, keyword);
            if (count > 0) {
                matched++;
                hits += Math.min(count, 3);
            }
        }
        double coverage = matched / (double) keywords.size();
        double density = Math.min(hits / 20.0, 1.0);
        return Math.round((coverage * 0.75 + density * 0.25) * 10000.0) / 10000.0;
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int fromIndex = 0;
        while (fromIndex < text.length()) {
            int index = text.indexOf(keyword, fromIndex);
            if (index < 0) {
                break;
            }
            count++;
            fromIndex = index + keyword.length();
        }
        return count;
    }

    private Map<String, Object> toRecallPayload(Question question, double score) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", question.getId());
        payload.put("type", "QUESTION");
        payload.put("sourceType", "QUESTION");
        payload.put("source_type", "QUESTION");
        payload.put("sourceId", question.getId());
        payload.put("source_id", question.getId());
        payload.put("title", Objects.toString(question.getTitle(), "Untitled question"));
        payload.put("recallMethod", "AUTO_KEYWORD");
        payload.put("recall_method", "AUTO_KEYWORD");
        payload.put("recallScore", score);
        payload.put("recall_score", score);
        payload.put("reason", score > 0 ? "Matched job, resume, or round keywords" : "Recent active question fallback");
        return payload;
    }

    private Map<String, Object> toRecallPayload(Experience experience, double score) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", experience.getId());
        payload.put("type", "EXPERIENCE");
        payload.put("sourceType", "EXPERIENCE");
        payload.put("source_type", "EXPERIENCE");
        payload.put("sourceId", experience.getId());
        payload.put("source_id", experience.getId());
        payload.put("title", Objects.toString(experience.getTitle(), "Untitled experience"));
        payload.put("recallMethod", "AUTO_KEYWORD");
        payload.put("recall_method", "AUTO_KEYWORD");
        payload.put("recallScore", score);
        payload.put("recall_score", score);
        payload.put("reason", score > 0 ? "Matched job, resume, or round keywords" : "Recent active experience fallback");
        return payload;
    }

    private record ScoredRecallItem(Map<String, Object> payload, double score, int sourceOrder) {
    }
}