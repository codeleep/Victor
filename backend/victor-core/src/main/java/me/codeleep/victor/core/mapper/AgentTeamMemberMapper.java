package me.codeleep.victor.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.codeleep.victor.core.entity.AgentTeamMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent团队成员Mapper
 */
@Mapper
public interface AgentTeamMemberMapper extends BaseMapper<AgentTeamMember> {
}
