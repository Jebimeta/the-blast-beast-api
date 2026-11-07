package com.theblastbeast.service;

import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.vo.CategoryResponse;
import com.theblastbeast.mapper.CategoryMapper;
import com.theblastbeast.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        log.debug("INIT - CategoryService -> list()");
        List<CategoryResponse> response = categoryRepository
                .findAllWithArticleCount(ArticleStatus.PUBLISHED)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
        log.debug("END - CategoryService -> list() - {} categorías", response.size());
        return response;
    }
}
