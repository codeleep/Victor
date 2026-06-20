package me.codeleep.victor.core.service.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.common.enums.ModelType;
import me.codeleep.victor.core.entity.AgentLlmConfig;
import me.codeleep.victor.core.mapper.AgentLlmConfigMapper;
import me.codeleep.victor.infra.agent.core.LlmProtocol;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 配置模块初始化器
 * 负责创建默认 LLM 配置，必须在其他 Agent 初始化器之前执行
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LlmConfigModuleInitializer implements ModuleInitializer {

    private final AgentLlmConfigMapper agentLlmConfigMapper;

    @Override
    public Map<String, Object> init(Long userId) {
        int llmCreated = 0;

        AgentLlmConfig existing = agentLlmConfigMapper.selectOne(
                new LambdaQueryWrapper<AgentLlmConfig>()
                        .eq(AgentLlmConfig::getUserId, userId)
                        .eq(AgentLlmConfig::getIsDefault, true)
                        .last("LIMIT 1"));
        if (existing == null) {
            AgentLlmConfig config = new AgentLlmConfig();
            config.setUserId(userId);
            config.setName("火山方舟默认LLM配置");
            config.setDescription("系统初始化创建的默认火山方舟 LLM 配置");
            config.setProvider("VOLCENGINE");
            config.setApiEndpoint(env("ARK_API_ENDPOINT", "https://ark.cn-beijing.volces.com/api/coding/v3"));
            config.setAuthParams(Map.of("apiKey", env("ARK_API_KEY", "")));
            config.setProtocol(LlmProtocol.VOLCENGINE);
            config.setModelName(env("ARK_MODEL_NAME", "ark-code-latest"));
            config.setModelType(ModelType.INFERENCE);
            config.setTemperature(new BigDecimal("0.70"));
            config.setMaxTokens(4096);
            config.setIsEnabled(!env("ARK_API_KEY", "").isBlank());
            config.setIsDefault(true);
            agentLlmConfigMapper.insert(config);
            llmCreated = 1;
            log.info("[LlmInit] 创建默认LLM配置: id={}", config.getId());
        } else if (migrateLegacyDoubao(existing)) {
            llmCreated = 1;
            log.info("[LlmInit] 修正旧 DOUBAO 配置为 VOLCENGINE: id={}", existing.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("llmCreated", llmCreated);
        return result;
    }

    /**
     * 修正历史遗留的 DOUBAO 协议/提供商值为 VOLCENGINE。
     * 枚举已从 DOUBAO 改名为 VOLCENGINE，若库里仍存旧值会导致 MyBatis EnumTypeHandler 反序列化失败。
     *
     * @return 是否发生了修正
     */
    private boolean migrateLegacyDoubao(AgentLlmConfig existing) {
        boolean changed = false;
        if (existing.getProtocol() != null && "DOUBAO".equals(existing.getProtocol().name())) {
            existing.setProtocol(LlmProtocol.VOLCENGINE);
            changed = true;
        }
        if ("DOUBAO".equals(existing.getProvider())) {
            existing.setProvider("VOLCENGINE");
            changed = true;
        }
        if (changed) {
            agentLlmConfigMapper.updateById(existing);
        }
        return changed;
    }
    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : fallback;
    }
}

