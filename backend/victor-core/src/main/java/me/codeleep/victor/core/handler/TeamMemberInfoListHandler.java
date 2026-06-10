package me.codeleep.victor.core.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import me.codeleep.victor.core.dto.TeamMemberInfo;

import java.util.List;

/**
 * TeamMemberInfo 列表的 jsonb TypeHandler
 */
public class TeamMemberInfoListHandler extends JsonbTypeHandler<List<TeamMemberInfo>> {

    public TeamMemberInfoListHandler() {
        super(new TypeReference<>() {});
    }
}
