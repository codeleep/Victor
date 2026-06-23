package me.codeleep.victor.core.service.interview;

import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;

import java.util.List;
import java.util.Map;

/**
 * 面试出题服务(出题线)。
 * <p>
 * 负责面试配置管理与异步出题, 状态机:
 * <pre>
 *   DRAFT -> GENERATING -> READY / GENERATE_FAILED
 * </pre>
 * 异步出题由 {@link me.codeleep.victor.core.service.interview.impl.QuestionGenerationExecutor} 执行,
 * 出题卡死(GENERATING 无在途任务)的自愈见其 {@code resumeIfStuck}。
 */
public interface InterviewQuestionService {

    /** 创建面试配置 */
    Long createConfig(InterviewConfigRequest request);

    /** 更新面试配置 */
    void updateConfig(Long id, InterviewConfigRequest request);

    /** 获取面试配置详情(读取时触发 GENERATING 自愈) */
    InterviewConfigVO getConfig(Long id);

    /** 获取用户的所有面试配置(读取时触发 GENERATING 自愈) */
    List<InterviewConfigVO> listConfigs();

    /** 删除面试配置 */
    void deleteConfig(Long id);

    /** 发布配置, 触发异步出题(DRAFT/GENERATE_FAILED -> GENERATING) */
    void publishConfig(Long id);

    /** 归档配置(仅 READY 可归档) */
    void archiveConfig(Long id);

    /** 预览召回资料列表 */
    List<Map<String, Object>> previewRecallItems(InterviewConfigRequest request);
}