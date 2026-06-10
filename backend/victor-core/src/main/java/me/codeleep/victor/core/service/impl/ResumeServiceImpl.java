package me.codeleep.victor.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.IngestStatus;
import me.codeleep.victor.common.enums.ResumeStatus;
import me.codeleep.victor.common.exception.BusinessException;
import me.codeleep.victor.common.result.ResultCode;
import me.codeleep.victor.common.context.UserContext;
import me.codeleep.victor.core.dto.ResumeVO;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.entity.Resume;
import me.codeleep.victor.infra.agent.llm.ChatClientFactory;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.core.mapper.ResumeMapper;
import me.codeleep.victor.core.service.ResumeService;
import me.codeleep.victor.core.service.converter.ResumeConverter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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
    private final ChatClientFactory chatClientFactory;
    private final AgentLlmConfigMapper agentLlmConfigMapper;

    @Value("${file.upload.path:${user.home}/victor/uploads}")
    private String uploadBasePath;

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

        // 1. 使用 Tika 从文件提取原始文本
        String extractedText = extractTextWithTika(resume.getFilePath());

        // 2. 获取系统默认 LLM 配置
        AgentLlmConfig llmConfig = agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .eq(AgentLlmConfig::getIsEnabled, true)
        );
        if (llmConfig == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未配置默认LLM，无法解析简历");
        }

        // 3. 调用 LLM 将简历内容转换为结构化 Markdown
        String apiKey = llmConfig.getAuthParams() != null ? (String) llmConfig.getAuthParams().getOrDefault("apiKey", "") : "";
        ChatClient chatClient = chatClientFactory.createChatClient(
                llmConfig.getProtocol(), llmConfig.getApiEndpoint(), apiKey,
                llmConfig.getModelName(),
                llmConfig.getTemperature() != null ? llmConfig.getTemperature().doubleValue() : 0.7,
                llmConfig.getMaxTokens() != null ? llmConfig.getMaxTokens() : 4096);
        String prompt = "你是一个简历解析专家。请将以下简历内容转换为结构化的 Markdown 格式。\n" +
                "要求：\n" +
                "1. 使用清晰的标题层级（# ## ###）\n" +
                "2. 保留所有关键信息：个人信息、教育经历、工作经历、项目经历、技能等\n" +
                "3. 使用列表、表格等 Markdown 元素增强可读性\n" +
                "4. 去除无关的格式噪音，只保留有意义的内容\n\n" +
                "简历内容：\n" + extractedText;

        String markdown = chatClient.prompt(prompt).call().content();

        // 4. 存储结构化 Markdown 到 rawText
        resume.setRawText(markdown);
        resume.setParsedContent(null);
        resume.setSummary(null);
        resume.setStatus(ResumeStatus.PARSED);
        resumeMapper.updateById(resume);
        log.info("简历解析完成: resumeId={}", resumeId);
    }

    /**
     * 使用 Apache Tika 从文件提取文本内容
     */
    private String extractTextWithTika(String filePath) {
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
            return handler.toString();
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
