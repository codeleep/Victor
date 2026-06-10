package me.codeleep.victor.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.autoconfigure.anthropic.AnthropicAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Victor AI面试助手 - 主启动类
 * 禁用Spring AI自动配置，LLM实例由用户动态配置
 */
@SpringBootApplication(scanBasePackages = "me.codeleep.victor", exclude = {
        OpenAiAutoConfiguration.class,
        AnthropicAutoConfiguration.class
})
@EnableAsync
public class VictorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorApplication.class, args);
    }
}
