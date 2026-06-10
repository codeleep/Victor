package me.codeleep.victor.web.controller;

import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewReportVO;
import me.codeleep.victor.core.service.dto.InterviewSessionVO;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import me.codeleep.victor.core.service.interview.InterviewService;
import me.codeleep.victor.core.service.report.ReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 面试会话控制器
 */
@RestController
@RequestMapping("/api/v1/interview-sessions")
@RequiredArgsConstructor
public class InterviewSessionController {

    private final InterviewService interviewService;
    private final ReportService reportService;

    /**
     * 创建面试会话
     */
    @PostMapping
    public Result<Long> createSession(@RequestParam Long configId) {
        Long id = interviewService.createSession(configId);
        return Result.success(id);
    }

    /**
     * 获取面试会话列表
     */
    @GetMapping
    public Result<List<InterviewSessionVO>> listSessions() {
        List<InterviewSessionVO> sessions = interviewService.listSessions();
        return Result.success(sessions);
    }

    /**
     * 获取面试会话详情
     */
    @GetMapping("/{id}")
    public Result<InterviewSessionVO> getSession(@PathVariable Long id) {
        InterviewSessionVO session = interviewService.getSession(id);
        return Result.success(session);
    }

    /**
     * 开始面试
     */
    @PostMapping("/{id}/start")
    public Result<Void> startInterview(@PathVariable Long id) {
        interviewService.startInterview(id);
        return Result.success();
    }

    /**
     * 提交回答
     */
    @PostMapping("/{id}/answer")
    public Result<String> submitAnswer(@PathVariable Long id, @RequestBody String answer) {
        String response = interviewService.submitAnswer(id, answer);
        return Result.success(response);
    }

    /**
     * 流式提交回答
     */
    @PostMapping(value = "/{id}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSubmitAnswer(@PathVariable Long id, @RequestBody String answer) {
        return interviewService.streamSubmitAnswer(id, answer);
    }

    /**
     * 跳过当前问题
     */
    @PostMapping("/{id}/skip")
    public Result<Void> skipQuestion(@PathVariable Long id) {
        interviewService.skipQuestion(id);
        return Result.success();
    }

    /**
     * 暂停面试
     */
    @PostMapping("/{id}/pause")
    public Result<Void> pauseInterview(@PathVariable Long id) {
        interviewService.pauseInterview(id);
        return Result.success();
    }

    /**
     * 恢复面试
     */
    @PostMapping("/{id}/resume")
    public Result<Void> resumeInterview(@PathVariable Long id) {
        interviewService.resumeInterview(id);
        return Result.success();
    }

    /**
     * 取消面试
     */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelInterview(@PathVariable Long id) {
        interviewService.cancelInterview(id);
        return Result.success();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> completeInterview(@PathVariable Long id) {
        interviewService.completeInterview(id);
        return Result.success();
    }

    /**
     * 获取面试报告
     */
    @GetMapping("/{id}/report")
    public Result<InterviewReportVO> getReport(@PathVariable Long id) {
        InterviewReportVO report = reportService.getReportBySessionId(id);
        return Result.success(report);
    }

    /**
     * 获取会话历史记录
     */
    @GetMapping("/{id}/history")
    public Result<List<InterviewTurnVO>> getHistory(@PathVariable Long id) {
        List<InterviewTurnVO> history = interviewService.getConversationHistory(id);
        return Result.success(history);
    }

    /**
     * 获取下一道题目
     */
    @GetMapping("/{id}/next-question")
    public Result<String> getNextQuestion(@PathVariable Long id) {
        String question = interviewService.getNextQuestion(id);
        return Result.success(question);
    }

    /**
     * 流式获取下一道题目
     */
    @GetMapping(value = "/{id}/next-question/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamNextQuestion(@PathVariable Long id) {
        return interviewService.streamNextQuestion(id);
    }

    /**
     * 获取提示
     */
    @GetMapping("/{id}/hint")
    public Result<String> getHint(@PathVariable Long id, @RequestParam(required = false) String currentQuestion) {
        String hint = interviewService.getHint(id, currentQuestion);
        return Result.success(hint);
    }

    /**
     * 流式获取提示
     */
    @GetMapping(value = "/{id}/hint/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGetHint(@PathVariable Long id, @RequestParam(required = false) String currentQuestion) {
        return interviewService.streamGetHint(id, currentQuestion);
    }

    /**
     * 获取面试总结
     */
    @GetMapping("/{id}/summary")
    public Result<String> getSummary(@PathVariable Long id) {
        String summary = interviewService.getSummary(id);
        return Result.success(summary);
    }

    /**
     * 流式获取面试总结
     */
    @GetMapping(value = "/{id}/summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGetSummary(@PathVariable Long id) {
        return interviewService.streamGetSummary(id);
    }
}
