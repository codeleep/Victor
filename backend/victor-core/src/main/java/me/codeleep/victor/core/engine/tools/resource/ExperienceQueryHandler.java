package me.codeleep.victor.core.engine.tools.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.ExperienceType;
import me.codeleep.victor.common.utils.SafeGet;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 经历资料查询处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperienceQueryHandler implements ResourceTypeHandler {

    private final ExperienceMapper experienceMapper;

    @Override
    public ResourceType getType() {
        return ResourceType.EXPERIENCE;
    }

    @Override
    public Map<String, Object> getExtraParametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> experienceType = new LinkedHashMap<>();
        experienceType.put("type", "string");
        experienceType.put("description", "经历类型：WORK(工作), EDUCATION(教育), PROJECT(项目), OTHER(其他)");
        experienceType.put("enum", Arrays.stream(ExperienceType.values()).map(ExperienceType::getValue).toList());
        properties.put("experience_type", experienceType);

        return properties;
    }

    @Override
    public Object query(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        Long userId = SafeGet.get(() -> Long.parseLong(arguments.get("user_id").toString()), null);
        String experienceType = (String) arguments.get("experience_type");
        Integer limit = SafeGet.get(() -> Integer.parseInt(arguments.get("limit").toString()), 10);

        LambdaQueryWrapper<Experience> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Experience::getUserId, userId);
        }
        if (experienceType != null && !experienceType.isEmpty()) {
            try {
                ExperienceType type = ExperienceType.valueOf(experienceType.toUpperCase());
                wrapper.eq(Experience::getType, type);
            } catch (IllegalArgumentException e) {
                log.warn("无效的经历类型: {}", experienceType);
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Experience::getTitle, keyword).or().like(Experience::getDescription, keyword));
        }
        wrapper.last("LIMIT " + limit);

        List<Experience> experiences = experienceMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Experience e : experiences) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("type", e.getType() != null ? e.getType().name() : null);
            item.put("title", e.getTitle());
            item.put("startDate", e.getStartDate());
            item.put("endDate", e.getEndDate());
            item.put("description", e.getDescription());
            item.put("skills", e.getSkills());
            results.add(item);
        }

        log.info("经历查询完成: keyword={}, type={}, results={}", keyword, experienceType, results.size());
        return Map.of("resource_type", "experience", "data", results, "total", results.size());
    }
}
