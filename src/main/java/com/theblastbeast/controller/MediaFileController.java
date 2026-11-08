package com.theblastbeast.controller;

import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.repository.MediaAssetRepository;
import com.theblastbeast.storage.StorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MediaFileController {

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;

    @GetMapping("/media/{*key}")
    public ResponseEntity<Resource> serve(@PathVariable String key) {
        String storageKey = key.startsWith("/") ? key.substring(1) : key;
        MediaAsset asset = mediaAssetRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));

        Resource resource = storageService.load(storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(resource);
    }
}
