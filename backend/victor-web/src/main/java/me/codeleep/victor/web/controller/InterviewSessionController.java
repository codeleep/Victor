package me.codeleep.victor.web.controller;

import lombok.RequiredArgsConstructor;
import me.codeleep.victor.common.result.Result;
import me.codeleep.victor.core.service.dto.InterviewSessionVO;
import me.codeleep.victor.core.service.dto.InterviewTurnVO;
import me.codeleep.victor.core.service.interview.InterviewSessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 面试会话控制器(面试线)。
 * 负责面试执行过程: 会话生命周期、问答推进、提示、总结、历史。
 * 报告相关接口见 {@link InterviewReportController}。
 */
@RestController
@RequestMapping("/api/v1/interview-sessions")
@RequiredArgsConstructor
public class InterviewSessionController {

    private final InterviewSessionService interviewSessionService;

    /** 创建面试会话 */
    @PostMapping
    public Result<Long> createSession(@RequestParam Long configId) {
        Long id = interviewSessionService.createSession(configId);
        return Result.success(id);
    }

    /** 获取面试会话列表 */
    @GetMapping
    public Result<List<InterviewSessionVO>> listSessions() {
        List<InterviewSessionVO> sessions = interviewSessionService.listSessions();
        return Result.success(sessions);
    }

    /** 获取面试会话详情 */
    @GetMapping("/{id}")
    public Result<InterviewSessionVO> getSession(@PathVariable Long id) {
        InterviewSessionVO session = interviewSessionService.getSession(id);
        return Result.success(session);
    }

    /** 开始面试 */
    @PostMapping("/{id}/start")
    public Result<Void> startInterview(@PathVariable Long id) {
        interviewSessionService.startInterview(id);
        return Result.success();
    }

    /** 提交回答 */
    @PostMapping("/{id}/answer")
    public Result<String> submitAnswer(@PathVariable Long id, @RequestBody String answer) {
        String response = interviewSessionService.submitAnswer(id, answer);
        return Result.success(response);
    }

    /** 流式提交回答 */
    @PostMapping(value = "/{id}/answer/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSubmitAnswer(@PathVariable Long id, @RequestBody String answer) {
        return interviewSessionService.streamSubmitAnswer(id, answer);
    }

    /** 跳过当前问题 */
    @PostMapping("/{id}/skip")
    public Result<Void> skipQuestion(@PathVariable Long id) {
        interviewSessionService.skipQuestion(id);
        return Result.success();
    }

    /** 暂停面试 */
    @PostMapping("/{id}/pause")
    public Result<Void> pauseInterview(@PathVariable Long id) {
        interviewSessionService.pauseInterview(id);
        return Result.success();
    }

    /** 恢复面试 */
    @PostMapping("/{id}/resume")
    public Result<Void> resumeInterview(@PathVariable Long id) {
        interviewSessionService.resumeInterview(id);
        return Result.success();
    }

    /** 取消面试 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelInterview(@PathVariable Long id) {
        interviewSessionService.cancelInterview(id);
        return Result.success();
    }

    /** 结束面试(触发异步评估) */
    @PostMapping("/{id}/complete")
    public Result<Void> completeInterview(@PathVariable Long id) {
        interviewSessionService.completeInterview(id);
        return Result.success();
    }

    /** 获取会话历史记录 */
    @GetMapping("/{id}/history")
    public Result<List<InterviewTurnVO>> getHistory(@PathVariable Long id) {
        List<InterviewTurnVO> history = interviewSessionService.getConversationHistory(id);
        return Result.success(history);
    }

    /** 获取下一道题目 */
    @GetMapping("/{id}/next-question")
    public Result<String> getNextQuestion(@PathVariable Long id) {
        String question = interviewSessionService.getNextQuestion(id);
        return Result.success(question);
    }

    /** 流式获取下一道题目 */
    @GetMapping(value = "/{id}/next-question/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamNextQuestion(@PathVariable Long id) {
        return interviewSessionService.streamNextQuestion(id);
    }

    /** 获取提示 */
    @GetMapping("/{id}/hint")
    public Result<String> getHint(@PathVariable Long id, @RequestParam(required = false) String currentQuestion) {
        String hint = interviewSessionService.getHint(id, currentQuestion);
        return Result.success(hint);
    }

    /** 流式获取提示 */
    @GetMapping(value = "/{id}/hint/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGetHint(@PathVariable Long id, @RequestParam(required = false) String currentQuestion) {
        return interviewSessionService.streamGetHint(id, currentQuestion);
    }

    /** 获取面试总结 */
    @GetMapping("/{id}/summary")
    public Result<String> getSummary(@PathVariable Long id) {
        String summary = interviewSessionService.getSummary(id);
        return Result.success(summary);
    }

    /** 流式获取面试总结 */
    @GetMapping(value = "/{id}/summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGetSummary(@PathVariable Long id) {
        return interviewSessionService.streamGetSummary(id);
    }
}