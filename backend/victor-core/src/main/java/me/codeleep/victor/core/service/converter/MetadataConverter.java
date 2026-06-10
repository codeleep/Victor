package me.codeleep.victor.core.service.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.codeleep.victor.core.dto.MetadataVO;
import me.codeleep.victor.core.entity.Metadata;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class MetadataConverter {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "extraData", ignore = true)
    public abstract MetadataVO toVO(Metadata metadata);

    public abstract List<MetadataVO> toVOList(List<Metadata> metadataList);

    @AfterMapping
    protected void fillExtraData(Metadata metadata, @MappingTarget MetadataVO vo) {
        if (metadata.getExtraData() != null && !metadata.getExtraData().isEmpty()) {
            try {
                Object extraData = objectMapper.readValue(metadata.getExtraData(), new TypeReference<Object>() {});
                vo.setExtraData(extraData);
            } catch (Exception e) {
                log.warn("解析extraData失败: {}", metadata.getExtraData(), e);
                vo.setExtraData(metadata.getExtraData());
            }
        }
    }
}
