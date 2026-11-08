package com.theblastbeast.controller;

import com.theblastbeast.api.AdminBandsApiDelegate;
import com.theblastbeast.domain.vo.BandResponse;
import com.theblastbeast.domain.vo.BandWriteRequest;
import com.theblastbeast.service.AdminBandService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBandController implements AdminBandsApiDelegate {

    private final AdminBandService adminBandService;

    @Override
    public ResponseEntity<List<BandResponse>> listAdminBands(String q) {
        return ResponseEntity.ok(adminBandService.list(q));
    }

    @Override
    public ResponseEntity<BandResponse> createAdminBand(BandWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminBandService.create(request));
    }

    @Override
    public ResponseEntity<BandResponse> updateAdminBand(Long id, BandWriteRequest request) {
        return ResponseEntity.ok(adminBandService.update(id, request));
    }

    @Override
    public ResponseEntity<Void> deleteAdminBand(Long id) {
        adminBandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
