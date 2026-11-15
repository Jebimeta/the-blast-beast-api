package com.theblastbeast.service;

import com.theblastbeast.domain.vo.ConcertSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "blastbeast.ticketmaster",
        name = "enabled",
        havingValue = "true")
public class ConcertSyncScheduler {

    private final ConcertSyncService concertSyncService;

    @Scheduled(
            cron = "${blastbeast.ticketmaster.sync-cron:0 0 */6 * * *}",
            zone = "Europe/Madrid")
    public void sync() {
        ConcertSyncResult result = concertSyncService.sync();
        if (result.getStatus() == ConcertSyncResult.StatusEnum.FAILED) {
            log.warn("La sincronización programada de la agenda falló: {}", result.getMessage());
        } else {
            log.info("Sincronización programada de la agenda: {}", result.getStatus());
        }
    }
}
