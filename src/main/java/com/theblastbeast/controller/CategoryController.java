package com.theblastbeast.controller;

import com.theblastbeast.api.CategoriesApiDelegate;
import com.theblastbeast.domain.vo.CategoryResponse;
import com.theblastbeast.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryController implements CategoriesApiDelegate {

    private final CategoryService categoryService;

    @Override
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        log.debug("INIT - CategoryController -> listCategories()");
        ResponseEntity<List<CategoryResponse>> response =
                ResponseEntity.ok(categoryService.list());
        log.debug("END - CategoryController -> listCategories()");
        return response;
    }
}
