package com.theblastbeast.integration.ticketmaster;

import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.service.ConcertGenre;
import com.theblastbeast.service.SlugService;
import com.theblastbeast.util.MadridTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Convierte un evento de Ticketmaster en el modelo interno de la agenda.
 *
 * <p>Descarta eventos sin fecha, sin recinto o fuera de España. Ticketmaster ya
 * filtra por país, pero la comprobación local evita importar datos erróneos si
 * el recinto declara otro país.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketmasterEventNormalizer {

    private static final int MAX_TITLE = 300;
    private static final int MAX_EXTERNAL_ID = 100;
    private static final int MAX_GENRE = 80;
    private static final int MAX_URL = 600;
    private static final int MAX_DESCRIPTION = 8000;
    private static final int MAX_ARTIST = 200;

    private final SlugService slugService;

    public Optional<NormalizedConcert> normalize(TicketmasterEvent event) {
        if (event == null || !StringUtils.hasText(event.id())
                || !StringUtils.hasText(event.name())) {
            return Optional.empty();
        }
        TicketmasterDates dates = event.dates();
        if (dates == null || dates.start() == null
                || !StringUtils.hasText(dates.start().localDate())
                || Boolean.TRUE.equals(dates.start().dateTBA())
                || Boolean.TRUE.equals(dates.start().dateTBD())) {
            return Optional.empty();
        }

        TicketmasterVenue venue = firstVenue(event);
        String city = venue == null || venue.city() == null ? null : venue.city().name();
        if (venue == null || !StringUtils.hasText(venue.name()) || !StringUtils.hasText(city)) {
            return Optional.empty();
        }
        String countryCode = venue.country() == null ? "ES" : venue.country().countryCode();
        if (StringUtils.hasText(countryCode) && !"ES".equalsIgnoreCase(countryCode)) {
            return Optional.empty();
        }

        ZoneId zone = zone(dates.timezone(), venue.timezone());
        OffsetDateTime startAt = parseDate(dates.start(), zone, false);
        if (startAt == null) {
            return Optional.empty();
        }
        OffsetDateTime endAt = parseDate(dates.end(), zone, true);
        if (endAt != null && !endAt.isAfter(startAt)) {
            endAt = null;
        }

        TicketmasterClassification classification = classification(event);
        String genreName = classification == null || classification.genre() == null
                ? null
                : classification.genre().name();
        String subgenreName = classification == null || classification.subGenre() == null
                ? null
                : classification.subGenre().name();
        ConcertGenre genre = ConcertGenre.from(genreName, subgenreName);

        PriceRange price = price(event.priceRanges());
        TicketmasterImage image = image(event.images());

        NormalizedVenue normalizedVenue = new NormalizedVenue(
                StringUtils.hasText(venue.id())
                        ? truncate(venue.id(), MAX_EXTERNAL_ID)
                        : slugService.slugify(venue.name() + "-" + city),
                truncate(venue.name().trim(), 200),
                truncate(city.trim(), 120),
                citySlug(city),
                venue.state() == null ? null : truncate(venue.state().name(), 120),
                StringUtils.hasText(countryCode) ? countryCode.toUpperCase(Locale.ROOT) : "ES",
                venue.address() == null ? null : truncate(venue.address().line1(), 300),
                decimal(venue.location() == null ? null : venue.location().latitude()),
                decimal(venue.location() == null ? null : venue.location().longitude()),
                StringUtils.hasText(venue.timezone()) ? venue.timezone() : dates.timezone());

        NormalizedConcert concert = new NormalizedConcert(
                truncate(event.id(), MAX_EXTERNAL_ID),
                truncate(event.name().trim(), MAX_TITLE),
                description(event),
                startAt,
                endAt,
                timeTba(dates.start()),
                status(dates.status()),
                genre.label(),
                genre.slug(),
                truncate(subgenreName, MAX_GENRE),
                truncate(event.url(), MAX_URL),
                truncate(event.url(), MAX_URL),
                price.min(),
                price.max(),
                price.currency(),
                image == null ? null : truncate(image.url(), MAX_URL),
                image == null ? null : "Ticketmaster",
                normalizedVenue,
                artists(event));

        log.trace("Evento de Ticketmaster normalizado: {} ({})", concert.title(),
                concert.externalId());
        return Optional.of(concert);
    }

    private TicketmasterVenue firstVenue(TicketmasterEvent event) {
        if (event.embedded() == null || event.embedded().venues() == null
                || event.embedded().venues().isEmpty()) {
            return null;
        }
        return event.embedded().venues().get(0);
    }

    private TicketmasterClassification classification(TicketmasterEvent event) {
        if (event.classifications() == null || event.classifications().isEmpty()) {
            return null;
        }
        return event.classifications().stream()
                .filter(item -> Boolean.TRUE.equals(item.primary()))
                .findFirst()
                .orElse(event.classifications().get(0));
    }

    private OffsetDateTime parseDate(TicketmasterDate date, ZoneId zone, boolean end) {
        if (date == null || !StringUtils.hasText(date.localDate())) {
            return null;
        }
        if (StringUtils.hasText(date.dateTime())) {
            try {
                return OffsetDateTime.parse(date.dateTime());
            } catch (DateTimeParseException ignored) {
                // Algunos eventos devuelven fecha local sin offset.
            }
            try {
                return LocalDateTime.parse(date.dateTime()).atZone(zone).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // Se intenta reconstruir con localDate/localTime.
            }
        }
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date.localDate());
        } catch (DateTimeParseException exception) {
            return null;
        }
        LocalTime localTime = parseTime(date.localTime());
        if (localTime == null) {
            localTime = end ? LocalTime.of(23, 59) : LocalTime.of(20, 0);
        }
        return localDate.atTime(localTime).atZone(zone).toOffsetDateTime();
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean timeTba(TicketmasterDate date) {
        return Boolean.TRUE.equals(date.timeTBA())
                || (!StringUtils.hasText(date.localTime())
                        && !StringUtils.hasText(date.dateTime()));
    }

    private ZoneId zone(String datesTimezone, String venueTimezone) {
        String value = StringUtils.hasText(datesTimezone) ? datesTimezone : venueTimezone;
        if (!StringUtils.hasText(value)) {
            return MadridTime.ZONE;
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            return MadridTime.ZONE;
        }
    }

    private ConcertStatus status(TicketmasterStatus status) {
        if (status == null || !StringUtils.hasText(status.code())) {
            return ConcertStatus.ON_SALE;
        }
        return switch (status.code().toLowerCase(Locale.ROOT)) {
            case "offsale" -> ConcertStatus.OFF_SALE;
            case "postponed" -> ConcertStatus.POSTPONED;
            case "rescheduled" -> ConcertStatus.RESCHEDULED;
            case "canceled", "cancelled" -> ConcertStatus.CANCELLED;
            default -> ConcertStatus.ON_SALE;
        };
    }

    private PriceRange price(List<TicketmasterPriceRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return new PriceRange(null, null, null);
        }
        BigDecimal min = null;
        BigDecimal max = null;
        String currency = null;
        for (TicketmasterPriceRange range : ranges) {
            if (range == null) {
                continue;
            }
            if (range.min() != null) {
                BigDecimal value = BigDecimal.valueOf(range.min());
                min = min == null || value.compareTo(min) < 0 ? value : min;
            }
            if (range.max() != null) {
                BigDecimal value = BigDecimal.valueOf(range.max());
                max = max == null || value.compareTo(max) > 0 ? value : max;
            }
            if (!StringUtils.hasText(currency) && StringUtils.hasText(range.currency())) {
                currency = range.currency().toUpperCase(Locale.ROOT);
            }
        }
        return new PriceRange(min, max, currency);
    }

    private TicketmasterImage image(List<TicketmasterImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(item -> item != null && StringUtils.hasText(item.url()))
                .filter(item -> "16_9".equals(item.ratio()))
                .max((left, right) -> Integer.compare(
                        left.width() == null ? 0 : left.width(),
                        right.width() == null ? 0 : right.width()))
                .orElseGet(() -> images.stream()
                        .filter(item -> item != null && StringUtils.hasText(item.url()))
                        .findFirst()
                        .orElse(null));
    }

    private List<NormalizedArtist> artists(TicketmasterEvent event) {
        if (event.embedded() == null || event.embedded().attractions() == null
                || event.embedded().attractions().isEmpty()) {
            return List.of();
        }
        List<NormalizedArtist> artists = new ArrayList<>();
        Set<String> slugs = new HashSet<>();
        int order = 0;
        for (TicketmasterAttraction attraction : event.embedded().attractions()) {
            if (attraction == null || !StringUtils.hasText(attraction.name())) {
                continue;
            }
            String baseSlug = slugService.slugify(attraction.name());
            if (!StringUtils.hasText(baseSlug)) {
                continue;
            }
            String slug = baseSlug;
            int suffix = 2;
            while (!slugs.add(slug)) {
                slug = baseSlug + "-" + suffix++;
            }
            artists.add(new NormalizedArtist(
                    truncate(attraction.id(), MAX_EXTERNAL_ID),
                    truncate(attraction.name().trim(), MAX_ARTIST),
                    truncate(slug, MAX_ARTIST),
                    order == 0,
                    order));
            order++;
        }
        return artists;
    }

    private String citySlug(String city) {
        String slug = slugService.slugify(city);
        return StringUtils.hasText(slug) ? slug : "espana";
    }

    private String description(TicketmasterEvent event) {
        String value = StringUtils.hasText(event.info()) ? event.info() : event.pleaseNote();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = HtmlUtils.htmlUnescape(value.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(text) ? truncate(text, MAX_DESCRIPTION) : null;
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record PriceRange(BigDecimal min, BigDecimal max, String currency) {
    }
}
