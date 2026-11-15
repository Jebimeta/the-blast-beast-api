package com.theblastbeast.controller;

import com.theblastbeast.api.AdminConcertsApiDelegate;
import com.theblastbeast.domain.vo.ConcertSyncResult;
import com.theblastbeast.service.ConcertSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminConcertController implements AdminConcertsApiDelegate {

    private final ConcertSyncService concertSyncService;

    @Override
    public ResponseEntity<ConcertSyncResult> syncConcerts() {
        log.info("INIT - AdminConcertController -> syncConcerts()");
        ConcertSyncResult result = concertSyncService.sync();
        log.info("END - AdminConcertController -> syncConcerts() - status={}", result.getStatus());
        return ResponseEntity.ok(result);
    }
}
