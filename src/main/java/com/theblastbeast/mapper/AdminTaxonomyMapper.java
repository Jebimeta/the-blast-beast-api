package com.theblastbeast.mapper;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.vo.BandResponse;
import com.theblastbeast.domain.vo.CategoryAdminResponse;
import com.theblastbeast.domain.vo.TagResponse;
import com.theblastbeast.repository.CategoryAdminView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminTaxonomyMapper {

    CategoryAdminResponse toCategoryResponse(CategoryAdminView view);

    BandResponse toBandResponse(Band band);

    TagResponse toTagResponse(Tag tag);
}
