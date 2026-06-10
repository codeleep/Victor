package me.codeleep.victor.core.service.interview;

import me.codeleep.victor.common.enums.InterviewConfigStatus;
import me.codeleep.victor.core.service.dto.InterviewConfigRequest;
import me.codeleep.victor.core.service.dto.InterviewConfigVO;
import me.codeleep.victor.core.service.dto.InterviewSessionVO;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 面试服务接口
 */
public interface InterviewService {

    /**
     * 创建面试配置
     */
    Long createConfig(InterviewConfigRequest request);

    /**
     * 更新面试配置
     */
    void updateConfig(Long id, InterviewConfigRequest request);

    /**
     * 获取面试配置
     */
    InterviewConfigVO getConfig(Long id);

    /**
     * 获取用户的所有面试配置
     */
    List<InterviewConfigVO> listConfigs();

    /**
     * 删除面试配置
     */
    void deleteConfig(Long id);

    /**
     * 发布面试配置
     */
    void publishConfig(Long id);

    /**
     * 归档面试配置
     */
    void archiveConfig(Long id);

    List<Map<String, Object>> previewRecallItems(InterviewConfigRequest request);

    /**
     * 创建面试会话
     */
    Long createSession(Long configId);

    /**
     * 获取面试会话
     */
    InterviewSessionVO getSession(Long id);

    /**
     * 获取用户的所有面试会话
     */
    List<InterviewSessionVO> listSessions();

    /**
     * 启动面试
     */
    void startInterview(Long sessionId);

    /**
     * 暂停面试
     */
    void pauseInterview(Long sessionId);

    /**
     * 恢复面试
     */
    void resumeInterview(Long sessionId);

    /**
     * 结束面试
     */
    void completeInterview(Long sessionId);

    /**
     * 跳过当前问题
     */
    void skipQuestion(Long sessionId);

    /**
     * 取消面试
     */
    void cancelInterview(Long sessionId);

    /**
     * 获取下一道题目
     */
    String getNextQuestion(Long sessionId);

    /**
     * 流式获取下一道题目
     */
    Flux<String> streamNextQuestion(Long sessionId);

    /**
     * 提交回答
     */
    String submitAnswer(Long sessionId, String answer);

    /**
     * 流式提交回答
     */
    Flux<String> streamSubmitAnswer(Long sessionId, String answer);

    /**
     * 获取提示
     */
    String getHint(Long sessionId, String currentQuestion);

    /**
     * 流式获取提示
     */
    Flux<String> streamGetHint(Long sessionId, String currentQuestion);

    /**
     * 获取面试总结
     */
    String getSummary(Long sessionId);

    /**
     * 流式获取面试总结
     */
    Flux<String> streamGetSummary(Long sessionId);

    /**
     * 获取对话历史
     */
    List<InterviewTurnVO> getConversationHistory(Long sessionId);

    /**
     * 更新会话状态
     */
    void updateSessionStatus(Long sessionId, InterviewConfigStatus status);
}
