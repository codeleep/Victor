package me.codeleep.victor.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.codeleep.victor.core.dto.QuestionQueryRequest;
import me.codeleep.victor.core.dto.QuestionRequest;
import me.codeleep.victor.core.dto.QuestionVO;
import me.codeleep.victor.core.entity.Question;

/**
 * 题目服务接口
 */
public interface QuestionService {

    /**
     * 创建题目
     */
    QuestionVO create(QuestionRequest request);

    /**
     * 根据ID获取题目
     */
    QuestionVO getById(Long id);

    /**
     * 更新题目
     */
    QuestionVO update(Long id, QuestionRequest request);

    /**
     * 删除题目
     */
    void delete(Long id);

    /**
     * 分页查询题目列表
     */
    Page<QuestionVO> list(QuestionQueryRequest request);

    /**
     * 获取实体
     */
    Question getEntityById(Long id);

    /**
     * 审核题目 - 批准
     */
    void approve(Long id);

    /**
     * 审核题目 - 拒绝
     */
    void reject(Long id);
}