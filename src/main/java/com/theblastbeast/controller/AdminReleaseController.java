package com.theblastbeast.controller;

import com.theblastbeast.api.AdminReleasesApiDelegate;
import com.theblastbeast.domain.vo.ReleaseAdminResponse;
import com.theblastbeast.domain.vo.ReleaseWriteRequest;
import com.theblastbeast.service.AdminReleaseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminReleaseController implements AdminReleasesApiDelegate {

    private final AdminReleaseService adminReleaseService;

    @Override
    public ResponseEntity<List<ReleaseAdminResponse>> listAdminReleases(String q, String month) {
        return ResponseEntity.ok(adminReleaseService.list(q, month));
    }

    @Override
    public ResponseEntity<ReleaseAdminResponse> createAdminRelease(ReleaseWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminReleaseService.create(request));
    }

    @Override
    public ResponseEntity<ReleaseAdminResponse> updateAdminRelease(
            Long id, ReleaseWriteRequest request) {
        return ResponseEntity.ok(adminReleaseService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminRelease(Long id) {
        adminReleaseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
