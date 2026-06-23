package me.codeleep.victor.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 记忆持久化实体
 * 存储 AgentScope AgentState 的 JSON 序列化形式，按 userId + sessionId + key 唯一
 */
@Data
@TableName("agent_memory")
public class AgentMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String sessionId;

    private String stateKey;

    @TableField("state_json")
    private String stateJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
