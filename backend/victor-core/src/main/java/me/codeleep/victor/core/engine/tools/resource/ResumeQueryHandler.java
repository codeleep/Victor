package me.codeleep.victor.core.engine.tools.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.utils.SafeGet;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.ResumeMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 简历资料查询处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeQueryHandler implements ResourceTypeHandler {

    private final ResumeMapper resumeMapper;

    @Override
    public ResourceType getType() {
        return ResourceType.RESUME;
    }

    @Override
    public Map<String, Object> getExtraParametersSchema() {
        // 简历查询暂无专属参数
        return Map.of();
    }

    @Override
    public Object query(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        Long userId = SafeGet.get(() -> Long.parseLong(arguments.get("user_id").toString()), null);
        Integer limit = SafeGet.get(() -> Integer.parseInt(arguments.get("limit").toString()), 10);

        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Resume::getUserId, userId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Resume::getName, keyword);
        }
        wrapper.last("LIMIT " + limit);

        List<Resume> resumes = resumeMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Resume r : resumes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("name", r.getName());
            item.put("rawText", r.getRawText());
            item.put("parsedContent", r.getParsedContent());
            item.put("summary", r.getSummary());
            item.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            results.add(item);
        }

        log.info("简历查询完成: keyword={}, userId={}, results={}", keyword, userId, results.size());
        return Map.of("resource_type", "resume", "data", results, "total", results.size());
    }
}
