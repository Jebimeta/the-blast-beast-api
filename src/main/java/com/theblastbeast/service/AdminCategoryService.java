package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.vo.CategoryAdminResponse;
import com.theblastbeast.domain.vo.CategoryWriteRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.AdminTaxonomyMapper;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;
    private final AdminTaxonomyMapper mapper;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public List<CategoryAdminResponse> list() {
        return categoryRepository.findAllForAdmin().stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }

    @Transactional
    public CategoryAdminResponse create(CategoryWriteRequest request) {
        Category category = new Category();
        category.setSlug(resolveSlug(request.getSlug(), request.getName(), null));
        apply(category, request);
        categoryRepository.save(category);
        log.info("END - AdminCategoryService -> create() - id={}, slug={}", category.getId(), category.getSlug());
        return toResponse(category);
    }

    @Transactional
    public CategoryAdminResponse update(Long id, CategoryWriteRequest request) {
        Category category = find(id);
        if (StringUtils.hasText(request.getSlug())) {
            category.setSlug(resolveSlug(request.getSlug(), request.getName(), id));
        }
        apply(category, request);
        categoryRepository.save(category);
        log.info("END - AdminCategoryService -> update() - id={}", id);
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (articleRepository.countByCategoryId(id) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        categoryRepository.delete(category);
        log.info("END - AdminCategoryService -> delete() - id={}", id);
    }

    private void apply(Category category, CategoryWriteRequest request) {
        category.setName(request.getName().trim());
        category.setDescription(StringUtils.hasText(request.getDescription())
                ? request.getDescription().trim()
                : null);
        category.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String resolveSlug(String requested, String name, Long currentId) {
        String base = slugService.slugify(StringUtils.hasText(requested) ? requested : name);
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo generar un slug a partir del nombre");
        }
        return slugService.unique(base, slug -> categoryRepository.existsBySlugAndIdNot(slug,
                currentId == null ? -1L : currentId));
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private CategoryAdminResponse toResponse(Category category) {
        CategoryAdminResponse response = new CategoryAdminResponse();
        response.setId(category.getId());
        response.setSlug(category.getSlug());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setSortOrder(category.getSortOrder());
        response.setArticleCount(articleRepository.countByCategoryId(category.getId()));
        return response;
    }
}
