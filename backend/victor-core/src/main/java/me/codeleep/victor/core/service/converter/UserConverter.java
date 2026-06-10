package me.codeleep.victor.core.service.converter;

import me.codeleep.victor.core.dto.UserVO;
import me.codeleep.victor.core.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class UserConverter {

    @Mapping(target = "token", ignore = true)
    public abstract UserVO toVO(User user);
}
