package com.theblastbeast.controller;

import com.theblastbeast.api.ReleasesApiDelegate;
import com.theblastbeast.domain.vo.ReleasePage;
import com.theblastbeast.domain.vo.ReleaseType;
import com.theblastbeast.service.ReleaseService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseController implements ReleasesApiDelegate {

    private static final MediaType CALENDAR =
            MediaType.valueOf("text/calendar;charset=UTF-8");
    private static final CacheControl CALENDAR_CACHE = CacheControl.maxAge(Duration.ofMinutes(10))
            .cachePublic()
            .staleWhileRevalidate(Duration.ofHours(1));

    private final ReleaseService releaseService;

    @Override
    public ResponseEntity<ReleasePage> listReleases(
            String month, String genre, ReleaseType type, Integer page, Integer size) {
        log.debug("INIT - ReleaseController -> listReleases()");
        ResponseEntity<ReleasePage> response =
                ResponseEntity.ok(releaseService.list(month, genre, type, page, size));
        log.debug("END - ReleaseController -> listReleases()");
        return response;
    }

    @Override
    public ResponseEntity<String> releaseCalendar(Integer months) {
        log.debug("INIT - ReleaseController -> releaseCalendar()");
        ResponseEntity<String> response = ResponseEntity.ok()
                .contentType(CALENDAR)
                .cacheControl(CALENDAR_CACHE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"radar-the-blast-beast.ics\"")
                .body(releaseService.calendar(months));
        log.debug("END - ReleaseController -> releaseCalendar()");
        return response;
    }
}
