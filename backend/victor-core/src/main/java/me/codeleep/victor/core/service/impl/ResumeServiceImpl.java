package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.SourceType;
import me.codeleep.victor.common.enums.ResumeStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.core.service.ResumeService;
import me.codeleep.victor.core.engine.AgentDefinitionFactory;
import me.codeleep.victor.core.entity.Agent;
import me.codeleep.victor.core.service.AgentService;
import me.codeleep.victor.infra.agent.core.AgentContext;
import me.codeleep.victor.infra.agent.core.AgentDefinition;
import me.codeleep.victor.infra.agent.core.AgentResult;
import me.codeleep.victor.infra.agent.runner.AgentFactory;
import me.codeleep.victor.infra.agent.runner.AgentRunner;
import io.agentscope.core.ReActAgent;
import me.codeleep.victor.core.service.converter.ResumeConverter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

/**
 * 简历服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeConverter resumeConverter;
    private final AgentService agentService;
    private final AgentDefinitionFactory agentDefinitionFactory;
    private final AgentFactory agentFactory;
    private final AgentRunner agentRunner;

    @Value("${file.upload.path:${user.home}/victor/uploads}")
    private String uploadBasePath;

    /** 简历解析 Agent 的 key（与 UtilityModuleInitializer 中注册一致） */
    private static final String KEY_RESUME_PARSER = "resume-parser";

    @Override
    @Transactional
    public ResumeVO upload(Long userId, String name, MultipartFile file) {
        try {
            // 创建上传目录
            Path uploadPath = Paths.get(uploadBasePath, "resumes").toAbsolutePath();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(uniqueFilename);

            // 保存文件
            file.transferTo(filePath.toFile());

            // 创建简历记录
            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setName(name);
            resume.setFileName(originalFilename);
            resume.setFilePath(filePath.toString());
            resume.setStatus(ResumeStatus.PENDING);
            resumeMapper.insert(resume);

            return resumeConverter.toVO(resume);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public void parse(Long resumeId) {
        Resume resume = getEntityById(resumeId);
        // 验证用户权限
        if (!resume.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此简历");
        }

        // 1. 使用 Tika 从文件提取原始文本与文件元数据（保留全部信息，作为不丢失的底稿）
        TikaExtractResult extractResult = extractTextWithTika(resume.getFilePath());
        String extractedText = extractResult.text();
        Map<String, String> tikaMetadata = extractResult.metadata();

        // 2. 通过 resume-parser Agent 将简历原文转换为结构化 Markdown（systemPrompt 来自 prompts/resume-parser.md，忠实保真不删减）
        Agent resumeParserAgent = agentService.getByKey(KEY_RESUME_PARSER);
        AgentDefinition agentDef = agentDefinitionFactory.build(resumeParserAgent);
        if (agentDef == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "简历解析Agent配置异常，无法解析简历");
        }
        ReActAgent agent = agentFactory.buildAgent(agentDef, null, String.valueOf(UserContext.getUserId()), null);
        AgentContext context = new AgentContext(null, UserContext.getUserId(), extractedText);
        context.setVariable("resumeFileName", resume.getFileName());
        context.getMetadata().put("agentKey", KEY_RESUME_PARSER);

        AgentResult agentResult = agentRunner.run(agent, context);
        if (!agentResult.isSuccess() || agentResult.getContent() == null) {
            log.error("简历解析Agent执行失败: resumeId={}, error={}", resumeId, agentResult.getErrorMessage());
            throw new BusinessException(ResultCode.RESUME_PARSE_FAILED,
                    agentResult.getErrorMessage() != null ? agentResult.getErrorMessage() : "简历解析Agent未返回有效内容");
        }
        String markdown = agentResult.getContent();

        // 3. 存储：rawText 为 Agent 整理后的可读 Markdown；Tika 提取的原始文本与文件元数据存入 parsedContent 作为底稿，确保不丢信息
        resume.setRawText(markdown);
        Map<String, Object> parsedContent = new HashMap<>();
        parsedContent.put("rawExtractedText", extractedText);
        parsedContent.put("rawCharCount", extractedText.length());
        parsedContent.put("sourceFileName", resume.getFileName());
        if (tikaMetadata != null) {
            parsedContent.put("fileMetadata", tikaMetadata);
        }
        resume.setParsedContent(parsedContent);
        resume.setSummary(null);
        resume.setStatus(ResumeStatus.PARSED);
        resumeMapper.updateById(resume);
        log.info("简历解析完成: resumeId={}, 原始文本字符数={}, Markdown字符数={}",
                resumeId, extractedText.length(), markdown != null ? markdown.length() : 0);
    }

    /** Tika 提取结果：原始文本 + 文件元数据，确保信息不丢失 */
    private record TikaExtractResult(String text, Map<String, String> metadata) {}

    /**
     * 使用 Apache Tika 从文件提取文本内容与元数据。
     * <p>BodyContentHandler(-1) 不限制长度，保留全部正文；同时收集 content-type、页数等元数据，
     * 作为不可丢失的底稿，避免后续 LLM 重写时漏抄信息后无法回溯。</p>
     */
    private TikaExtractResult extractTextWithTika(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "简历文件不存在");
        }
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            try (InputStream stream = Files.newInputStream(path)) {
                parser.parse(stream, handler, metadata, context);
            }
            Map<String, String> metaMap = new LinkedHashMap<>();
            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }
            return new TikaExtractResult(handler.toString(), metaMap);
        } catch (Exception e) {
            log.error("Tika 提取文本失败: {}", filePath, e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "简历文件解析失败");
        }
    }

    @Override
    public ResumeVO getById(Long id) {
        Resume resume = getEntityById(id);
        return resumeConverter.toVO(resume);
    }

    @Override
    public List<ResumeVO> listByUserId() {
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resume::getUserId, UserContext.getUserId())
                .orderByDesc(Resume::getCreatedAt);
        List<Resume> resumes = resumeMapper.selectList(wrapper);
        return resumes.stream().map(resumeConverter::toVO).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Resume resume = getEntityById(id);
        // 验证用户权限
        if (!resume.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此简历");
        }

        // 删除文件
        try {
            Path filePath = Paths.get(resume.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            log.warn("删除简历文件失败: {}", resume.getFilePath(), e);
        }

        // 删除简历记录
        resumeMapper.deleteById(id);
    }

    @Override
    public Resume getEntityById(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BusinessException(ResultCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    @Override
    @Transactional
    public void updateRawText(Long id, String rawText) {
        Resume resume = getEntityById(id);
        // 验证用户权限
        if (!resume.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此简历");
        }

        resume.setRawText(rawText);

        // 同步更新 parsedContent
        Map<String, Object> parsedContent = resume.getParsedContent();
        if (parsedContent == null) {
            parsedContent = new HashMap<>();
        }
        parsedContent.put("fullText", rawText);
        parsedContent.put("charCount", rawText.length());

        // 重新提取联系信息
        String[] lines = rawText.split("\\n");
        StringBuilder contactInfo = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*\\d{11}.*") || trimmed.matches(".*[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}.*")) {
                contactInfo.append(trimmed).append(" ");
            }
        }
        if (!contactInfo.isEmpty()) {
            parsedContent.put("contactInfo", contactInfo.toString().trim());
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("lineCount", lines.length);
        summary.put("hasContent", true);

        resume.setParsedContent(parsedContent);
        resume.setSummary(summary);
        resumeMapper.updateById(resume);
    }

    @Override
    @Transactional
    public void approve(Long id) {
        getEntityById(id); // 验证存在
        Resume updateEntity = new Resume();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.ACTIVE);
        resumeMapper.updateById(updateEntity);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        getEntityById(id); // 验证存在
        Resume updateEntity = new Resume();
        updateEntity.setId(id);
        updateEntity.setIngestStatus(IngestStatus.REJECTED);
        resumeMapper.updateById(updateEntity);
    }

}

