package com.theblastbeast.controller;

import com.theblastbeast.api.BandsApiDelegate;
import com.theblastbeast.domain.vo.BandDetail;
import com.theblastbeast.service.BandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BandController implements BandsApiDelegate {

    private final BandService bandService;

    @Override
    public ResponseEntity<BandDetail> getBand(String slug) {
        log.debug("INIT - BandController -> getBand() - slug={}", slug);
        ResponseEntity<BandDetail> response = ResponseEntity.ok(bandService.detail(slug));
        log.debug("END - BandController -> getBand() - slug={}", slug);
        return response;
    }
}
