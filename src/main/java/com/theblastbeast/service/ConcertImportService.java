package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.integration.ticketmaster.NormalizedArtist;
import com.theblastbeast.integration.ticketmaster.NormalizedConcert;
import com.theblastbeast.integration.ticketmaster.NormalizedVenue;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.ConcertRepository;
import com.theblastbeast.repository.VenueRepository;
import com.theblastbeast.util.MadridTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Persiste los eventos normalizados y mantiene el cartel de cada concierto.
 *
 * <p>La importación es idempotente: el identificador externo decide si el
 * concierto se crea o se actualiza. La curaduría editorial ({@code hidden} y
 * {@code featured}) se conserva entre sincronizaciones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertImportService {

    private static final String SOURCE = "TICKETMASTER";
    private static final int BATCH_SIZE = 500;
    private static final int MAX_SLUG_LENGTH = 200;

    private final ConcertRepository concertRepository;
    private final VenueRepository venueRepository;
    private final BandRepository bandRepository;
    private final SlugService slugService;

    @Transactional
    public ImportStats importEvents(List<NormalizedConcert> events, OffsetDateTime seenAt) {
        if (events == null || events.isEmpty()) {
            return new ImportStats(0, 0);
        }

        Map<String, Band> bandsBySlug = bandRepository.findAll().stream()
                .collect(Collectors.toMap(Band::getSlug, Function.identity(), (left, right) -> left));
        Map<String, Venue> venues = loadVenues(events);
        Map<String, Concert> concerts = loadConcerts(events);

        int created = 0;
        int updated = 0;
        for (NormalizedConcert event : events) {
            Venue venue = upsertVenue(event.venue(), venues);
            Concert concert = concerts.get(event.externalId());
            boolean isNew = concert == null;
            if (isNew) {
                concert = new Concert();
                concert.setSource(SOURCE);
                concert.setExternalId(event.externalId());
                concert.setSlug(uniqueSlug(event));
                concert.setHidden(false);
                concert.setFeatured(false);
                created++;
            } else {
                updated++;
            }
            apply(concert, event, venue, seenAt, bandsBySlug);
            concertRepository.save(concert);
            concerts.put(event.externalId(), concert);
        }

        log.info("Importados {} eventos de Ticketmaster: {} nuevos, {} actualizados",
                events.size(), created, updated);
        return new ImportStats(created, updated);
    }

    @Transactional
    public int archiveNotSeen(OffsetDateTime cutoff, OffsetDateTime now, OffsetDateTime until) {
        return concertRepository.archiveNotSeen(
                SOURCE, cutoff, now, until, ConcertStatus.ARCHIVED);
    }

    private Map<String, Venue> loadVenues(List<NormalizedConcert> events) {
        List<String> ids = events.stream()
                .map(event -> event.venue().externalId())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, Venue> venues = new HashMap<>();
        for (List<String> batch : partition(ids)) {
            venueRepository.findBySourceAndExternalIdIn(SOURCE, batch)
                    .forEach(venue -> venues.put(venue.getExternalId(), venue));
        }
        return venues;
    }

    private Map<String, Concert> loadConcerts(List<NormalizedConcert> events) {
        List<String> ids = events.stream()
                .map(NormalizedConcert::externalId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, Concert> concerts = new HashMap<>();
        for (List<String> batch : partition(ids)) {
            concertRepository.findBySourceAndExternalIdIn(SOURCE, batch)
                    .forEach(concert -> concerts.put(concert.getExternalId(), concert));
        }
        return concerts;
    }

    private Venue upsertVenue(NormalizedVenue normalized, Map<String, Venue> venues) {
        Venue venue = venues.get(normalized.externalId());
        if (venue == null) {
            venue = new Venue();
            venue.setSource(SOURCE);
            venue.setExternalId(normalized.externalId());
            venues.put(normalized.externalId(), venue);
        }
        venue.setName(normalized.name());
        venue.setCity(normalized.city());
        venue.setCitySlug(normalized.citySlug());
        venue.setRegion(normalized.region());
        venue.setCountryCode(StringUtils.hasText(normalized.countryCode())
                ? normalized.countryCode()
                : "ES");
        venue.setAddress(normalized.address());
        venue.setLatitude(normalized.latitude());
        venue.setLongitude(normalized.longitude());
        venue.setTimezone(normalized.timezone());
        if (venue.getId() == null) {
            venue = venueRepository.save(venue);
            venues.put(normalized.externalId(), venue);
        }
        return venue;
    }

    private void apply(Concert concert, NormalizedConcert event, Venue venue,
            OffsetDateTime seenAt, Map<String, Band> bandsBySlug) {
        concert.setTitle(event.title());
        concert.setDescription(event.description());
        concert.setStartAt(event.startAt());
        concert.setEndAt(event.endAt());
        concert.setTimeTba(event.timeTba());
        concert.setStatus(event.status());
        concert.setGenre(event.genre());
        concert.setGenreSlug(event.genreSlug());
        concert.setSubgenre(event.subgenre());
        concert.setTicketUrl(event.ticketUrl());
        concert.setSourceUrl(event.sourceUrl());
        concert.setPriceMin(event.priceMin());
        concert.setPriceMax(event.priceMax());
        concert.setCurrency(event.currency());
        concert.setImageUrl(event.imageUrl());
        concert.setImageAttribution(event.imageAttribution());
        concert.setVenue(venue);
        concert.setLastSeenAt(seenAt);
        reconcileArtists(concert, event.artists(), bandsBySlug);
    }

    private void reconcileArtists(Concert concert, List<NormalizedArtist> artists,
            Map<String, Band> bandsBySlug) {
        Map<String, com.theblastbeast.domain.entity.ConcertArtist> existing = concert.getArtists()
                .stream()
                .collect(Collectors.toMap(
                        com.theblastbeast.domain.entity.ConcertArtist::getSlug,
                        Function.identity(),
                        (left, right) -> left));
        Set<String> seen = new HashSet<>();
        for (NormalizedArtist artist : artists) {
            com.theblastbeast.domain.entity.ConcertArtist entity = existing.get(artist.slug());
            if (entity == null) {
                entity = new com.theblastbeast.domain.entity.ConcertArtist();
                entity.setSlug(artist.slug());
                concert.addArtist(entity);
            }
            entity.setExternalId(artist.externalId());
            entity.setName(artist.name());
            entity.setHeadliner(artist.headliner());
            entity.setSortOrder(artist.sortOrder());
            entity.setBand(bandsBySlug.get(artist.slug()));
            seen.add(artist.slug());
        }
        concert.getArtists().removeIf(artist -> !seen.contains(artist.getSlug()));
    }

    private String uniqueSlug(NormalizedConcert event) {
        String base = slugService.slugify(event.title());
        if (!StringUtils.hasText(base)) {
            base = "concierto";
        }
        String city = StringUtils.hasText(event.venue().citySlug())
                ? event.venue().citySlug()
                : "espana";
        String date = MadridTime.toLocalDate(event.startAt()).toString();
        String candidate = truncate(base, 120) + "-" + date + "-" + truncate(city, 60);
        candidate = truncate(candidate, MAX_SLUG_LENGTH);
        return slugService.unique(candidate, concertRepository::existsBySlug);
    }

    private List<List<String>> partition(List<String> values) {
        List<List<String>> batches = new ArrayList<>();
        for (int index = 0; index < values.size(); index += BATCH_SIZE) {
            batches.add(values.subList(index, Math.min(index + BATCH_SIZE, values.size())));
        }
        return batches;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ImportStats(int created, int updated) {
    }
}
