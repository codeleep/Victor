package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录失败记录实体
 */
@Data
@TableName("login_failure_log")
public class LoginFailureLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 尝试时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime attemptedAt;
}
