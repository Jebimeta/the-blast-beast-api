package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Release;
import com.theblastbeast.domain.vo.ReleaseAdminResponse;
import com.theblastbeast.domain.vo.ReleaseSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReleaseMapper {

    @Mapping(source = "kind", target = "type")
    ReleaseSummary toSummary(Release release);

    @Mapping(source = "kind", target = "type")
    @Mapping(source = "band.id", target = "bandId")
    ReleaseAdminResponse toAdminResponse(Release release);
}
