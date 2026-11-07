package com.theblastbeast.mapper;

import com.theblastbeast.domain.vo.CategoryResponse;
import com.theblastbeast.repository.CategoryCountView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(CategoryCountView view);
}
