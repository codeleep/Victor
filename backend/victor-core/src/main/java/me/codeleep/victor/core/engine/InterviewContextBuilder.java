package me.codeleep.victor.core.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.InterviewConfig;
import me.codeleep.victor.core.entity.InterviewQuestion;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.mapper.ResumeMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 面试上下文构建器 - 共享的 runtime context 拼装逻辑
 * 出题、面试、评估等场景统一复用，避免 formatJob/formatResume 等方法重复
 *
 * 面试上下文分为两类：
 * - 面试基础信息（岗位+简历）：整场面试不变，作为长期背景注入一次
 * - 当前题目/配置：换题时更新，单独注入便于替换
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewContextBuilder {

    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;

    /**
     * 构建面试基础信息（岗位 + 简历），整场面试不变的长期背景
     */
    public String buildInterviewBackground(InterviewConfig config) {
        Job job = config.getJobId() != null ? jobMapper.selectById(config.getJobId()) : null;
        Resume resume = config.getResumeId() != null ? resumeMapper.selectById(config.getResumeId()) : null;

        return """
                你是一名面试官，以下是本次面试的基础背景信息，请在整个面试过程中参考：

                ## 岗位
                %s

                ## 简历
                %s
                """.formatted(
                formatJob(job),
                formatResume(resume)
        );
    }

    /**
     * 构建当前题目与面试配置上下文，换题时需要更新
     */
    public String buildCurrentQuestionContext(InterviewConfig config, InterviewQuestion currentQuestion) {
        return """
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
                """.formatted(
                currentQuestion.getId(),
                currentQuestion.getQuestionText(),
                currentQuestion.getAnswerHint() != null ? currentQuestion.getAnswerHint() : Map.of(),
                currentQuestion.getSourceRecallRefs() != null ? currentQuestion.getSourceRecallRefs() : List.of(),
                config.getRounds() != null ? config.getRounds() : List.of(),
                config.getDifficultyConfig() != null ? config.getDifficultyConfig() : Map.of(),
                config.getDurationMinutes() != null ? config.getDurationMinutes() : "",
                config.getRecallItems() != null ? config.getRecallItems() : List.of()
        );
    }

    public String formatJob(Job job) {
        if (job == null) {
            return "未选择岗位";
        }
        return "岗位名称: " + job.getName() + "\n"
                + "岗位描述: " + nullToEmpty(job.getDescription()) + "\n"
                + "技能要求: " + (job.getRequiredSkills() != null ? job.getRequiredSkills() : List.of()) + "\n"
                + "领域: " + (job.getDomains() != null ? job.getDomains() : List.of());
    }

    public String formatResume(Resume resume) {
        if (resume == null) {
            return "未选择简历";
        }
        return "简历名称: " + resume.getName() + "\n"
                + "简历摘要: " + (resume.getSummary() != null ? resume.getSummary() : Map.of()) + "\n"
                + "结构化内容: " + (resume.getParsedContent() != null ? resume.getParsedContent() : Map.of()) + "\n"
                + "原文片段: " + abbreviate(resume.getRawText(), 2000);
    }

    public String formatJobBrief(Job job) {
        if (job == null) return "未选择岗位";
        return job.getName() + (job.getRequiredSkills() != null ? "，技能要求: " + job.getRequiredSkills() : "");
    }

    public String formatResumeBrief(Resume resume) {
        if (resume == null) return "无简历信息";
        return resume.getName() + (resume.getSummary() != null ? "，摘要: " + abbreviate(resume.getSummary().toString(), 500) : "");
    }

    public String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
