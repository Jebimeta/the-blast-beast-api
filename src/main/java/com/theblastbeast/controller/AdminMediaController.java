package com.theblastbeast.controller;

import com.theblastbeast.api.AdminMediaApiDelegate;
import com.theblastbeast.domain.vo.MediaAssetResponse;
import com.theblastbeast.domain.vo.MediaPage;
import com.theblastbeast.domain.vo.MediaUpdateRequest;
import com.theblastbeast.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminMediaController implements AdminMediaApiDelegate {

    private final MediaService mediaService;

    @Override
    public ResponseEntity<MediaPage> listAdminMedia(Integer page, Integer size, String q) {
        return ResponseEntity.ok(mediaService.list(page, size, q));
    }

    @Override
    public ResponseEntity<MediaAssetResponse> uploadAdminMedia(MultipartFile file,
            String altText, String credit) {
        log.debug("INIT - AdminMediaController -> uploadAdminMedia()");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.upload(file, altText, credit));
    }

    @Override
    public ResponseEntity<MediaAssetResponse> updateAdminMedia(Long id, MediaUpdateRequest request) {
        return ResponseEntity.ok(mediaService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminMedia(Long id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
