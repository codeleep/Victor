package me.codeleep.victor.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 面试配置与运行态状态机。
 * <p>
 * 正常推进路径(order 递增, 一般不允许回退):
 * <pre>
 *   DRAFT(0) -> GENERATING(10) -> READY(20) -> IN_PROGRESS(30)
 *           -> COMPLETED(40) -> REPORT_GENERATING(50) -> REPORT_COMPLETED(60) -> ARCHIVED(70)
 * </pre>
 * 旁支/失败态:
 * <ul>
 *   <li>GENERATE_FAILED(15): 出题失败, 可回退到 GENERATING 重试</li>
 *   <li>PAUSED(25): 暂停, 与 IN_PROGRESS 互转</li>
 *   <li>REPORT_FAILED(55): 评估失败, 可回退到 REPORT_GENERATING 重试</li>
 *   <li>ABANDONED(35): 放弃, 终态</li>
 * </ul>
 * 转移合法性统一由 {@link #canTransitionTo(InterviewConfigStatus)} 校验。
 */
@Getter
@AllArgsConstructor
public enum InterviewConfigStatus {

    DRAFT("DRAFT", "Draft", 0),
    GENERATING("GENERATING", "Generating questions", 10),
    GENERATE_FAILED("GENERATE_FAILED", "Question generation failed", 15),
    READY("READY", "Questions ready", 20),
    IN_PROGRESS("IN_PROGRESS", "Interview in progress", 30),
    PAUSED("PAUSED", "Interview paused", 25),
    ABANDONED("ABANDONED", "Interview abandoned", 35),
    COMPLETED("COMPLETED", "Interview completed", 40),
    REPORT_GENERATING("REPORT_GENERATING", "Report generating", 50),
    REPORT_FAILED("REPORT_FAILED", "Report generation failed", 55),
    REPORT_COMPLETED("REPORT_COMPLETED", "Report completed", 60),
    ARCHIVED("ARCHIVED", "Archived", 70);

    private final String value;
    private final String description;
    /** 状态在推进路径上的序号, 越大越靠后; 用于判断前进/回退 */
    private final int order;

    /**
     * 判断从当前状态流转到目标状态是否合法。
     * <p>
     * 规则:
     * <ul>
     *   <li>相同状态: 合法(幂等)</li>
     *   <li>前进(order 增大)且在允许的前进路径上: 合法</li>
     *   <li>失败态回退到可重试态: GENERATE_FAILED->GENERATING, REPORT_FAILED->REPORT_GENERATING: 合法</li>
     *   <li>IN_PROGRESS 与 PAUSED 互转: 合法</li>
     *   <li>其余回退: 非法</li>
     * </ul>
     *
     * @param target 目标状态
     * @return 是否允许流转
     */
    public boolean canTransitionTo(InterviewConfigStatus target) {
        if (this == target) {
            return true;
        }
        // 失败态回退到可重试态
        if (this == GENERATE_FAILED && target == GENERATING) {
            return true;
        }
        if (this == REPORT_FAILED && target == REPORT_GENERATING) {
            return true;
        }
        // 进行中与暂停互转
        if ((this == IN_PROGRESS && target == PAUSED) || (this == PAUSED && target == IN_PROGRESS)) {
            return true;
        }
        // 放弃: 从进行中相关状态进入终态
        if (target == ABANDONED && (this == IN_PROGRESS || this == PAUSED)) {
            return true;
        }
        // 前进: order 增大, 且目标不是失败态/暂停态/放弃态(这些已由上述分支处理)
        if (target.order > this.order
                && target != GENERATE_FAILED
                && target != PAUSED
                && target != ABANDONED
                && target != REPORT_FAILED) {
            return true;
        }
        return false;
    }

    /**
     * 判断当前状态是否表示面试已开始(进入会话及之后), 用于禁止编辑配置等。
     */
    public boolean isInterviewStarted() {
        return this == IN_PROGRESS || this == PAUSED || this == ABANDONED
                || this == COMPLETED || this == REPORT_GENERATING
                || this == REPORT_COMPLETED || this == REPORT_FAILED;
    }

    /**
     * 判断当前状态是否为可编辑配置的状态(面试未开始)。
     */
    public boolean isEditable() {
        return this == DRAFT || this == GENERATING || this == GENERATE_FAILED || this == READY;
    }
}