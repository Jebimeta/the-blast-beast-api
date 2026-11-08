package com.theblastbeast.controller;

import com.theblastbeast.api.AdminCategoriesApiDelegate;
import com.theblastbeast.domain.vo.CategoryAdminResponse;
import com.theblastbeast.domain.vo.CategoryWriteRequest;
import com.theblastbeast.service.AdminCategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminCategoryController implements AdminCategoriesApiDelegate {

    private final AdminCategoryService adminCategoryService;

    @Override
    public ResponseEntity<List<CategoryAdminResponse>> listAdminCategories() {
        return ResponseEntity.ok(adminCategoryService.list());
    }

    @Override
    public ResponseEntity<CategoryAdminResponse> createAdminCategory(CategoryWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminCategoryService.create(request));
    }

    @Override
    public ResponseEntity<CategoryAdminResponse> updateAdminCategory(Long id,
            CategoryWriteRequest request) {
        return ResponseEntity.ok(adminCategoryService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminCategory(Long id) {
        adminCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
