package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.entity.Metadata;
import me.codeleep.victor.core.mapper.MetadataMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据模块初始化器
 * 负责初始化系统使用的全局字典元数据（题型、难度、Agent类型等）。
 * 元数据为全局共享数据（与用户无关），通过分类是否存在做幂等判断，
 * 对未初始化的分类进行补齐，对已存在的分类保持不动。
 * 排在所有业务模块之前执行，确保下拉项数据先就绪。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class MetadataModuleInitializer implements ModuleInitializer {

    private final MetadataMapper metadataMapper;

    /**
     * 预置字典：分类 -> [编码, 名称, 描述, 排序]
     */
    private static final List<String[]> PRESET = List.of(
            // 题型
            new String[]{"QUESTION_TYPE", "TECHNICAL", "技术题", "技术类题目", "10"},
            new String[]{"QUESTION_TYPE", "BEHAVIORAL", "行为题", "行为类题目", "20"},
            new String[]{"QUESTION_TYPE", "SHORT_ANSWER", "简答题", "简答类题目", "30"},
            new String[]{"QUESTION_TYPE", "MULTIPLE_CHOICE", "选择题", "选择类题目", "40"},
            new String[]{"QUESTION_TYPE", "CODING", "编程题", "编程类题目", "50"},
            // 难度
            new String[]{"DIFFICULTY", "EASY", "初级", "初级难度", "10"},
            new String[]{"DIFFICULTY", "MEDIUM", "中级", "中级难度", "20"},
            new String[]{"DIFFICULTY", "HARD", "高级", "高级难度", "30"},
            // 面试模式
            new String[]{"INTERVIEW_MODE", "VOICE", "语音", "语音面试", "10"},
            new String[]{"INTERVIEW_MODE", "TEXT", "文字", "文字面试", "20"},
            // 面试题来源类型
            new String[]{"INTERVIEW_TYPE", "BANK", "题库题", "来自题库的题目", "10"},
            new String[]{"INTERVIEW_TYPE", "GENERATED", "AI生成题", "AI动态生成的题目", "20"},
            // 面试状态
            new String[]{"INTERVIEW_STATUS", "DRAFT", "草稿", "面试配置草稿", "10"},
            new String[]{"INTERVIEW_STATUS", "GENERATING", "生成中", "正在生成题目", "20"},
            new String[]{"INTERVIEW_STATUS", "GENERATE_FAILED", "生成失败", "题目生成失败", "30"},
            new String[]{"INTERVIEW_STATUS", "READY", "就绪", "题目就绪", "40"},
            new String[]{"INTERVIEW_STATUS", "IN_PROGRESS", "进行中", "面试进行中", "50"},
            new String[]{"INTERVIEW_STATUS", "PAUSED", "已暂停", "面试已暂停", "60"},
            new String[]{"INTERVIEW_STATUS", "COMPLETED", "已完成", "面试已完成", "70"},
            new String[]{"INTERVIEW_STATUS", "ABANDONED", "已放弃", "面试已放弃", "80"},
            new String[]{"INTERVIEW_STATUS", "ARCHIVED", "已归档", "面试已归档", "90"},
            // Agent 类型
            new String[]{"AGENT_TYPE", "INTERVIEW", "面试Agent", "负责面试提问", "10"},
            new String[]{"AGENT_TYPE", "EVALUATION", "评估Agent", "负责面试评估", "20"},
            new String[]{"AGENT_TYPE", "SEARCH", "检索Agent", "负责资料检索", "30"},
            // 用户状态
            new String[]{"USER_STATUS", "ACTIVE", "正常", "用户正常", "10"},
            new String[]{"USER_STATUS", "LOCKED", "锁定", "用户已锁定", "20"},
            new String[]{"USER_STATUS", "DELETED", "已删除", "用户已删除", "30"},
            // 简历状态
            new String[]{"RESUME_STATUS", "PENDING", "待解析", "简历待解析", "10"},
            new String[]{"RESUME_STATUS", "PARSED", "已解析", "简历已解析", "20"},
            new String[]{"RESUME_STATUS", "EMBEDDED", "已嵌入", "简历已向量化", "30"},
            // 文档状态
            new String[]{"DOCUMENT_STATUS", "PENDING_REVIEW", "待审核", "文档待审核", "10"},
            new String[]{"DOCUMENT_STATUS", "ACTIVE", "有效", "文档有效", "20"},
            new String[]{"DOCUMENT_STATUS", "REJECTED", "已拒绝", "文档已拒绝", "30"},
            new String[]{"DOCUMENT_STATUS", "FAILED", "导入失败", "文档导入失败", "40"},
            // 插件状态
            new String[]{"PLUGIN_STATUS", "ACTIVE", "启用", "插件已启用", "10"},
            new String[]{"PLUGIN_STATUS", "INACTIVE", "禁用", "插件已禁用", "20"}
    );

    @Override
    public Map<String, Object> init(Long userId) {
        int created = 0;
        int skipped = 0;

        for (String[] item : PRESET) {
            String category = item[0];
            String code = item[1];

            boolean exists = metadataMapper.selectCount(
                    new LambdaQueryWrapper<Metadata>()
                            .eq(Metadata::getCategory, category)
                            .eq(Metadata::getCode, code)
                            .last("LIMIT 1")) > 0;
            if (exists) {
                skipped++;
                continue;
            }

            Metadata metadata = new Metadata();
            metadata.setCategory(category);
            metadata.setCode(code);
            metadata.setName(item[2]);
            metadata.setDescription(item[3]);
            metadata.setSortOrder(Integer.parseInt(item[4]));
            metadata.setIsActive(true);
            metadataMapper.insert(metadata);
            created++;
        }

        log.info("[MetadataInit] 元数据初始化完成: 新增={}, 跳过(已存在)={}", created, skipped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metadataCreated", created);
        result.put("metadataSkipped", skipped);
        return result;
    }
}
