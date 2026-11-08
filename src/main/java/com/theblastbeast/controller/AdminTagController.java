package com.theblastbeast.controller;

import com.theblastbeast.api.AdminTagsApiDelegate;
import com.theblastbeast.domain.vo.TagResponse;
import com.theblastbeast.domain.vo.TagWriteRequest;
import com.theblastbeast.service.AdminTagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminTagController implements AdminTagsApiDelegate {

    private final AdminTagService adminTagService;

    @Override
    public ResponseEntity<List<TagResponse>> listAdminTags(String q) {
        return ResponseEntity.ok(adminTagService.list(q));
    }

    @Override
    public ResponseEntity<TagResponse> createAdminTag(TagWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminTagService.create(request));
    }

    @Override
    public ResponseEntity<TagResponse> updateAdminTag(Long id, TagWriteRequest request) {
        return ResponseEntity.ok(adminTagService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminTag(Long id) {
        adminTagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
