package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Release;
import com.theblastbeast.domain.entity.ReleaseKind;
import com.theblastbeast.domain.vo.ReleaseFacet;
import com.theblastbeast.domain.vo.ReleasePage;
import com.theblastbeast.domain.vo.ReleaseType;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ReleaseMapper;
import com.theblastbeast.properties.SiteProperties;
import com.theblastbeast.repository.ReleaseRepository;
import jakarta.persistence.criteria.Predicate;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Radar de lanzamientos: listado público por mes y calendario iCalendar.
 *
 * <p>El radar es editorial: la redacción decide qué entra y cuándo. Así se
 * evitan fechas incompletas o duplicadas de agregadores externos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_CALENDAR_MONTHS = 6;
    private static final int MAX_CALENDAR_MONTHS = 24;
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ICS_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final ReleaseRepository releaseRepository;
    private final ReleaseMapper releaseMapper;
    private final SlugService slugService;
    private final SiteProperties siteProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReleasePage list(String month, String genre, ReleaseType type, Integer page, Integer size) {
        log.debug("INIT - ReleaseService -> list() - month={}, genre={}, type={}", month, genre, type);
        YearMonth selected = parseMonth(month);
        LocalDate from = selected.atDay(1);
        LocalDate to = selected.atEndOfMonth();
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String genreSlug = StringUtils.hasText(genre) ? slugService.slugify(genre) : null;
        ReleaseKind kind = type == null ? null : ReleaseKind.valueOf(type.name());

        Specification<Release> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("hidden")));
            predicates.add(builder.between(root.get("releaseDate"), from, to));
            if (genreSlug != null) {
                predicates.add(builder.equal(root.get("genreSlug"), genreSlug));
            }
            if (kind != null) {
                predicates.add(builder.equal(root.get("kind"), kind));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<Release> result = releaseRepository.findAll(specification,
                PageRequest.of(safePage, safeSize,
                        Sort.by("releaseDate").ascending().and(Sort.by("title").ascending())));

        List<Release> monthReleases = releaseRepository
                .findByHiddenFalseAndReleaseDateBetweenOrderByReleaseDateAscTitleAsc(from, to);

        ReleasePage response = new ReleasePage();
        response.setContent(result.getContent().stream().map(releaseMapper::toSummary).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setFirst(result.isFirst());
        response.setLast(result.isLast());
        response.setMonth(selected.toString());
        response.setGenres(genreFacets(monthReleases));
        response.setTypes(typeFacets(monthReleases));

        log.debug("END - ReleaseService -> list() - {} lanzamientos", result.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public String calendar(Integer months) {
        log.debug("INIT - ReleaseService -> calendar() - months={}", months);
        int safeMonths = months == null
                ? DEFAULT_CALENDAR_MONTHS
                : Math.min(Math.max(months, 1), MAX_CALENDAR_MONTHS);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.withDayOfMonth(1);
        LocalDate to = from.plusMonths(safeMonths).minusDays(1);
        List<Release> releases = releaseRepository
                .findByHiddenFalseAndReleaseDateBetweenOrderByReleaseDateAscTitleAsc(from, to);

        String base = baseUrl();
        String timestamp = ICS_TIMESTAMP.format(Instant.now(clock).atOffset(ZoneOffset.UTC));
        StringBuilder ics = new StringBuilder(2048);
        appendLine(ics, "BEGIN:VCALENDAR");
        appendLine(ics, "VERSION:2.0");
        appendLine(ics, "PRODID:-//The Blast Beast//Radar de lanzamientos//ES");
        appendLine(ics, "CALSCALE:GREGORIAN");
        appendLine(ics, "METHOD:PUBLISH");
        appendLine(ics, "X-WR-CALNAME:" + escape("The Blast Beast — Radar de lanzamientos"));
        appendLine(ics, "X-WR-CALDESC:"
                + escape("Discos, EPs y singles seleccionados por la redacción."));
        appendLine(ics, "X-WR-TIMEZONE:Europe/Madrid");

        for (Release release : releases) {
            appendLine(ics, "BEGIN:VEVENT");
            appendLine(ics, "UID:release-" + release.getId() + "@" + host(base));
            appendLine(ics, "DTSTAMP:" + timestamp);
            appendLine(ics, "DTSTART;VALUE=DATE:" + ICS_DATE.format(release.getReleaseDate()));
            appendLine(ics, "DTEND;VALUE=DATE:"
                    + ICS_DATE.format(release.getReleaseDate().plusDays(1)));
            appendLine(ics, "SUMMARY:" + escape(summary(release)));
            String description = description(release);
            if (StringUtils.hasText(description)) {
                appendLine(ics, "DESCRIPTION:" + escape(description));
            }
            if (StringUtils.hasText(release.getListenUrl())) {
                appendLine(ics, "URL:" + escape(release.getListenUrl()));
            }
            appendLine(ics, "CATEGORIES:" + escape(typeLabel(release.getKind())));
            appendLine(ics, "END:VEVENT");
        }

        appendLine(ics, "END:VCALENDAR");
        log.debug("END - ReleaseService -> calendar() - {} lanzamientos", releases.size());
        return ics.toString();
    }

    private List<ReleaseFacet> genreFacets(List<Release> releases) {
        Map<String, ReleaseFacet> facets = new LinkedHashMap<>();
        for (Release release : releases) {
            if (!StringUtils.hasText(release.getGenreSlug())) {
                continue;
            }
            ReleaseFacet facet = facets.computeIfAbsent(release.getGenreSlug(), slug -> {
                ReleaseFacet created = new ReleaseFacet();
                created.setValue(slug);
                created.setLabel(StringUtils.hasText(release.getGenre())
                        ? release.getGenre()
                        : slug);
                created.setCount(0L);
                return created;
            });
            facet.setCount(facet.getCount() + 1);
        }
        return facets.values().stream()
                .sorted(Comparator.comparingLong(ReleaseFacet::getCount).reversed()
                        .thenComparing(ReleaseFacet::getLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ReleaseFacet> typeFacets(List<Release> releases) {
        Map<ReleaseKind, Long> counts = new LinkedHashMap<>();
        for (Release release : releases) {
            counts.merge(release.getKind(), 1L, Long::sum);
        }
        List<ReleaseFacet> facets = new ArrayList<>();
        for (ReleaseKind kind : ReleaseKind.values()) {
            Long count = counts.get(kind);
            if (count == null || count == 0) {
                continue;
            }
            ReleaseFacet facet = new ReleaseFacet();
            facet.setValue(kind.name().toLowerCase(Locale.ROOT));
            facet.setLabel(typeLabel(kind));
            facet.setCount(count);
            facets.add(facet);
        }
        return facets;
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El mes debe tener el formato YYYY-MM");
        }
    }

    private String summary(Release release) {
        return release.getArtistName() + " — " + release.getTitle()
                + " (" + typeLabel(release.getKind()) + ")";
    }

    private String description(Release release) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(release.getGenre())) {
            parts.add("Género: " + release.getGenre());
        }
        if (StringUtils.hasText(release.getLabel())) {
            parts.add("Sello: " + release.getLabel());
        }
        if (StringUtils.hasText(release.getNotes())) {
            parts.add(release.getNotes());
        }
        return String.join("\n", parts);
    }

    private String typeLabel(ReleaseKind kind) {
        return switch (kind) {
            case ALBUM -> "Álbum";
            case EP -> "EP";
            case SINGLE -> "Single";
            case LIVE -> "Directo";
            case COMPILATION -> "Recopilatorio";
        };
    }

    private String baseUrl() {
        String base = siteProperties.baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String host(String base) {
        try {
            String host = URI.create(base).getHost();
            return StringUtils.hasText(host) ? host : "theblastbeast.com";
        } catch (IllegalArgumentException exception) {
            return "theblastbeast.com";
        }
    }

    private void appendLine(StringBuilder ics, String line) {
        ics.append(fold(line)).append("\r\n");
    }

    /** Pliega líneas largas a 75 octetos, como exige RFC 5545. */
    private String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= 75) {
            return line;
        }
        StringBuilder folded = new StringBuilder(line.length() + 16);
        int bytesInLine = 0;
        for (int index = 0; index < line.length(); ) {
            int codePoint = line.codePointAt(index);
            String character = new String(Character.toChars(codePoint));
            int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytesInLine + length > 73) {
                folded.append("\r\n ");
                bytesInLine = 1;
            }
            folded.append(character);
            bytesInLine += length;
            index += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
