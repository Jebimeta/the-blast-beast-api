package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.vo.BandDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BandMapper {

    @Mapping(target = "articles", ignore = true)
    BandDetail toDetail(Band band);
}
