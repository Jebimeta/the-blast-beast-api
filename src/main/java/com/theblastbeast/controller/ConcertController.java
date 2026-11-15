package com.theblastbeast.controller;

import com.theblastbeast.api.ConcertsApiDelegate;
import com.theblastbeast.domain.vo.ConcertDetail;
import com.theblastbeast.domain.vo.ConcertFacets;
import com.theblastbeast.domain.vo.ConcertPage;
import com.theblastbeast.repository.ConcertFilter;
import com.theblastbeast.service.ConcertService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertController implements ConcertsApiDelegate {

    private final ConcertService concertService;

    @Override
    public ResponseEntity<ConcertPage> listConcerts(
            Integer page,
            Integer size,
            String genre,
            String city,
            String band,
            LocalDate from,
            LocalDate to) {
        log.debug("INIT - ConcertController -> listConcerts()");
        ConcertPage response = concertService.list(
                new ConcertFilter(genre, city, band, from, to), page, size);
        log.debug("END - ConcertController -> listConcerts()");
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ConcertFacets> getConcertFacets() {
        log.debug("INIT - ConcertController -> getConcertFacets()");
        ConcertFacets response = concertService.facets();
        log.debug("END - ConcertController -> getConcertFacets()");
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ConcertDetail> getConcert(String slug) {
        log.debug("INIT - ConcertController -> getConcert() - slug={}", slug);
        ConcertDetail response = concertService.detail(slug);
        log.debug("END - ConcertController -> getConcert() - slug={}", slug);
        return ResponseEntity.ok(response);
    }
}
