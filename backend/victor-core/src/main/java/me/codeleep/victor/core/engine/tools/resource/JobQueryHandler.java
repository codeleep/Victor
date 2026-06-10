package me.codeleep.victor.core.engine.tools.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.utils.SafeGet;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.mapper.JobMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 岗位资料查询处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobQueryHandler implements ResourceTypeHandler {

    private final JobMapper jobMapper;

    @Override
    public ResourceType getType() {
        return ResourceType.JOB;
    }

    @Override
    public Map<String, Object> getExtraParametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("type", "array");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "string");
        skills.put("items", item);
        skills.put("description", "按技能要求筛选");
        properties.put("skills", skills);

        return properties;
    }

    @Override
    public Object query(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        Long userId = SafeGet.get(() -> Long.parseLong(arguments.get("user_id").toString()), null);
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) arguments.get("skills");
        Integer limit = SafeGet.get(() -> Integer.parseInt(arguments.get("limit").toString()), 10);

        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Job::getUserId, userId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Job::getName, keyword).or().like(Job::getDescription, keyword));
        }
        if (skills != null && !skills.isEmpty()) {
            for (String skill : skills) {
                wrapper.like(Job::getRequiredSkills, skill);
            }
        }
        wrapper.last("LIMIT " + limit);

        List<Job> jobs = jobMapper.selectList(wrapper);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Job j : jobs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", j.getId());
            item.put("name", j.getName());
            item.put("description", j.getDescription());
            item.put("requiredSkills", j.getRequiredSkills());
            item.put("experienceYears", j.getExperienceYears());
            item.put("education", j.getEducation());
            item.put("salaryRange", j.getSalaryRange());
            item.put("domains", j.getDomains());
            results.add(item);
        }

        log.info("岗位查询完成: keyword={}, skills={}, results={}", keyword, skills, results.size());
        return Map.of("resource_type", "job", "data", results, "total", results.size());
    }
}
