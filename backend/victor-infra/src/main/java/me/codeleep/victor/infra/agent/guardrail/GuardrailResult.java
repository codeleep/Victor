package me.codeleep.victor.infra.agent.guardrail;

import lombok.Builder;
import lombok.Data;

/**
 * Guardrail 校验结果
 */
@Data
@Builder
public class GuardrailResult {

    /**
     * 是否通过校验
     */
    private boolean passed;

    /**
     * 原因说明
     */
    private String reason;

    /**
     * 严重程度（INFO, WARNING, ERROR）
     */
    @Builder.Default
    private Severity severity = Severity.INFO;

    public static GuardrailResult pass() {
        return GuardrailResult.builder().passed(true).build();
    }

    public static GuardrailResult fail(String reason) {
        return GuardrailResult.builder()
                .passed(false)
                .reason(reason)
                .severity(Severity.ERROR)
                .build();
    }

    public static GuardrailResult fail(String reason, Severity severity) {
        return GuardrailResult.builder()
                .passed(false)
                .reason(reason)
                .severity(severity)
                .build();
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
