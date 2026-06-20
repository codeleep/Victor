package me.codeleep.victor.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Victor AI面试助手 - 主启动类
 */
@SpringBootApplication(scanBasePackages = "me.codeleep.victor")
@EnableAsync
public class VictorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VictorApplication.class, args);
    }
}
