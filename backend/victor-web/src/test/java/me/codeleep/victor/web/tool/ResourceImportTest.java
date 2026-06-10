package me.codeleep.victor.web.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.codeleep.victor.common.enums.*;
import me.codeleep.victor.core.entity.Experience;
import me.codeleep.victor.core.entity.Job;
import me.codeleep.victor.core.entity.Question;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.ExperienceMapper;
import me.codeleep.victor.core.mapper.JobMapper;
import me.codeleep.victor.core.mapper.QuestionMapper;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.web.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 资料批量导入脚本（岗位、简历、经历、题目）
 * 通过 MyBatis-Plus Mapper 写入数据库
 */
@Rollback(false)
class ResourceImportTest extends BaseApiTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private JobMapper jobMapper;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private ExperienceMapper experienceMapper;
    @Autowired
    private QuestionMapper questionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("批量导入岗位数据")
    void importJobs() throws Exception {
        List<Map<String, Object>> items = loadJson("res/jobs.json");
        System.out.println("=== 共读取 " + items.size() + " 个岗位 ===");

        int success = 0, fail = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            try {
                Job job = new Job();
                job.setName((String) item.get("name"));
                job.setDescription((String) item.get("description"));
                job.setRequiredSkills(toStringList(item.get("requiredSkills")));
                job.setExperienceYears(toInt(item.get("experienceYears")));
                job.setEducation((String) item.get("education"));
                job.setSalaryRange((String) item.get("salaryRange"));
                job.setDomains(toStringList(item.get("domains")));
                job.setIngestStatus(IngestStatus.ACTIVE);
                job.setSourceType(SourceType.USER);
                job.setUserId(USER_ID);
                jobMapper.insert(job);
                System.out.println("[成功] #" + (i + 1) + " id=" + job.getId() + " " + job.getName());
                success++;
            } catch (Exception e) {
                System.err.println("[失败] #" + (i + 1) + " " + e.getMessage());
                fail++;
            }
        }
        System.out.println("=== 岗位导入完成: 成功 " + success + ", 失败 " + fail + " ===");
    }

    @Test
    @DisplayName("批量导入简历数据")
    void importResumes() throws Exception {
        List<Map<String, Object>> items = loadJson("res/resumes.json");
        System.out.println("=== 共读取 " + items.size() + " 份简历 ===");

        int success = 0, fail = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            try {
                Resume resume = new Resume();
                resume.setName((String) item.get("name"));
                resume.setRawText((String) item.get("rawText"));
                resume.setParsedContent(toMap(item.get("parsedContent")));
                resume.setSummary(toMap(item.get("summary")));
                resume.setStatus(ResumeStatus.PARSED);
                resume.setIngestStatus(IngestStatus.ACTIVE);
                resume.setSourceType(SourceType.USER);
                resume.setUserId(USER_ID);
                resumeMapper.insert(resume);
                System.out.println("[成功] #" + (i + 1) + " id=" + resume.getId() + " " + resume.getName());
                success++;
            } catch (Exception e) {
                System.err.println("[失败] #" + (i + 1) + " " + e.getMessage());
                fail++;
            }
        }
        System.out.println("=== 简历导入完成: 成功 " + success + ", 失败 " + fail + " ===");
    }

    @Test
    @DisplayName("批量导入经历数据")
    void importExperiences() throws Exception {
        List<Map<String, Object>> items = loadJson("res/experiences.json");
        System.out.println("=== 共读取 " + items.size() + " 条经历 ===");

        int success = 0, fail = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            try {
                Experience exp = new Experience();
                exp.setType(ExperienceType.valueOf((String) item.get("type")));
                exp.setTitle((String) item.get("title"));
                exp.setStartDate(LocalDate.parse((String) item.get("startDate")));
                String endDate = (String) item.get("endDate");
                exp.setEndDate(endDate != null ? LocalDate.parse(endDate) : null);
                exp.setDescription((String) item.get("description"));
                exp.setSkills(toStringList(item.get("skills")));
                exp.setIngestStatus(IngestStatus.ACTIVE);
                exp.setSourceType(SourceType.USER);
                exp.setUserId(USER_ID);
                experienceMapper.insert(exp);
                System.out.println("[成功] #" + (i + 1) + " id=" + exp.getId() + " " + exp.getTitle());
                success++;
            } catch (Exception e) {
                System.err.println("[失败] #" + (i + 1) + " " + e.getMessage());
                fail++;
            }
        }
        System.out.println("=== 经历导入完成: 成功 " + success + ", 失败 " + fail + " ===");
    }

    @Test
    @DisplayName("批量导入题目数据")
    void importQuestions() throws Exception {
        List<Map<String, Object>> items = loadJson("res/questions.json");
        System.out.println("=== 共读取 " + items.size() + " 道题目 ===");

        int success = 0, fail = 0;
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            try {
                Question q = new Question();
                q.setTitle((String) item.get("title"));
                q.setDescription((String) item.get("description"));
                q.setType(QuestionType.valueOf((String) item.get("type")));
                q.setTags(toStringList(item.get("tags")));
                q.setDifficulty(Difficulty.valueOf((String) item.get("difficulty")));
                q.setReferenceAnswer((String) item.get("referenceAnswer"));
                q.setSource(QuestionSource.USER);
                q.setIngestStatus(IngestStatus.ACTIVE);
                q.setSourceType(SourceType.USER);
                q.setUserId(USER_ID);
                questionMapper.insert(q);
                System.out.println("[成功] #" + (i + 1) + " id=" + q.getId() + " " + q.getTitle());
                success++;
            } catch (Exception e) {
                System.err.println("[失败] #" + (i + 1) + " " + e.getMessage());
                fail++;
            }
        }
        System.out.println("=== 题目导入完成: 成功 " + success + ", 失败 " + fail + " ===");
    }

    // ==================== 工具方法 ====================

    private List<Map<String, Object>> loadJson(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assert is != null : "找不到文件: " + path;
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        return null;
    }
}
