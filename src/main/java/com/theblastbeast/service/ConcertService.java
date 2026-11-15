package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.domain.vo.ConcertDayCount;
import com.theblastbeast.domain.vo.ConcertDetail;
import com.theblastbeast.domain.vo.ConcertFacets;
import com.theblastbeast.domain.vo.ConcertPage;
import com.theblastbeast.domain.vo.FacetValue;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ConcertMapper;
import com.theblastbeast.repository.ConcertDayCountView;
import com.theblastbeast.repository.ConcertDateRangeView;
import com.theblastbeast.repository.ConcertFilter;
import com.theblastbeast.repository.ConcertRepository;
import com.theblastbeast.util.MadridTime;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int ARTIST_FACETS = 50;
    private static final int CALENDAR_DAYS = 365;
    private static final List<ConcertStatus> EXCLUDED =
            List.of(ConcertStatus.CANCELLED, ConcertStatus.ARCHIVED);

    private final ConcertRepository concertRepository;
    private final ConcertMapper concertMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConcertPage list(ConcertFilter filter, Integer page, Integer size) {
        log.debug("INIT - ConcertService -> list()");
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Concert> result = concertRepository.findAll(
                specification(filter),
                PageRequest.of(safePage, safeSize,
                        Sort.by("startAt").ascending().and(Sort.by("id").ascending())));

        ConcertPage response = new ConcertPage();
        response.setContent(result.getContent().stream().map(concertMapper::toSummary).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setFirst(result.isFirst());
        response.setLast(result.isLast());
        log.debug("END - ConcertService -> list() - {} conciertos", result.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public ConcertDetail detail(String slug) {
        log.debug("INIT - ConcertService -> detail() - slug={}", slug);
        Concert concert = concertRepository.findBySlugWithDetails(slug)
                .filter(item -> !item.isHidden())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        ConcertDetail response = concertMapper.toDetail(concert);
        log.debug("END - ConcertService -> detail() - slug={}", slug);
        return response;
    }

    @Transactional(readOnly = true)
    public ConcertFacets facets() {
        log.debug("INIT - ConcertService -> facets()");
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<FacetValue> genres = concertRepository.countByGenre(EXCLUDED, now).stream()
                .map(row -> facet((String) row[0], (String) row[1], (Long) row[2]))
                .toList();
        List<FacetValue> cities = concertRepository.countByCity(EXCLUDED, now).stream()
                .map(row -> facet((String) row[0], (String) row[1], (Long) row[2]))
                .toList();
        List<FacetValue> artists = concertRepository.countByArtist(
                        EXCLUDED, now, PageRequest.of(0, ARTIST_FACETS)).stream()
                .map(row -> facet((String) row[0], (String) row[1], (Long) row[2]))
                .toList();
        List<ConcertDayCount> days = concertRepository.countByDay(
                        now, now.plusDays(CALENDAR_DAYS)).stream()
                .map(view -> dayCount(view))
                .toList();

        ConcertFacets response = new ConcertFacets();
        response.setGenres(genres);
        response.setCities(cities);
        response.setArtists(artists);
        response.setDays(days);

        ConcertDateRangeView range = concertRepository.findDateRange(EXCLUDED, now);
        if (range != null) {
            if (range.getEarliest() != null) {
                response.setEarliest(MadridTime.toLocalDate(range.getEarliest()));
            }
            if (range.getLatest() != null) {
                response.setLatest(MadridTime.toLocalDate(range.getLatest()));
            }
        }
        log.debug("END - ConcertService -> facets() - {} géneros, {} ciudades",
                genres.size(), cities.size());
        return response;
    }

    private Specification<Concert> specification(ConcertFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "La fecha inicial no puede ser posterior a la final");
        }
        return Specification.allOf(
                visible(),
                upcoming(filter),
                genre(filter.genre()),
                city(filter.city()),
                band(filter.band()));
    }

    private Specification<Concert> visible() {
        return (root, query, builder) -> builder.and(
                builder.isFalse(root.get("hidden")),
                builder.not(root.get("status").in(EXCLUDED)));
    }

    private Specification<Concert> upcoming(ConcertFilter filter) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime today = MadridTime.startOfDay(MadridTime.today(clock));
        OffsetDateTime lower = today;
        if (filter.from() != null) {
            OffsetDateTime requested = MadridTime.startOfDay(filter.from());
            if (requested.isAfter(today)) {
                lower = requested;
            }
        }
        OffsetDateTime from = lower;
        return (root, query, builder) -> {
            Predicate predicate = builder.greaterThanOrEqualTo(root.get("startAt"), from);
            if (filter.to() != null) {
                predicate = builder.and(predicate, builder.lessThan(
                        root.get("startAt"), MadridTime.endOfDayExclusive(filter.to())));
            }
            return predicate;
        };
    }

    private Specification<Concert> genre(String genre) {
        if (!StringUtils.hasText(genre)) {
            return (root, query, builder) -> builder.conjunction();
        }
        String value = genre.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> builder.equal(root.get("genreSlug"), value);
    }

    private Specification<Concert> city(String city) {
        if (!StringUtils.hasText(city)) {
            return (root, query, builder) -> builder.conjunction();
        }
        String value = city.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            Join<Concert, Venue> venue = root.join("venue");
            return builder.equal(venue.get("citySlug"), value);
        };
    }

    private Specification<Concert> band(String band) {
        if (!StringUtils.hasText(band)) {
            return (root, query, builder) -> builder.conjunction();
        }
        String value = band.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            Join<Concert, com.theblastbeast.domain.entity.ConcertArtist> artists =
                    root.join("artists");
            query.distinct(true);
            return builder.or(
                    builder.equal(builder.lower(artists.get("slug")), value),
                    builder.like(builder.lower(artists.get("name")), "%" + value + "%"));
        };
    }

    private FacetValue facet(String value, String label, Long count) {
        String slug = StringUtils.hasText(value) ? value : ConcertGenre.OTHER.slug();
        String text = StringUtils.hasText(label) ? label : ConcertGenre.labelForSlug(slug);
        return new FacetValue(slug, text, count == null ? 0 : count);
    }

    private ConcertDayCount dayCount(ConcertDayCountView view) {
        return new ConcertDayCount(view.getDay(), view.getTotal());
    }
}
