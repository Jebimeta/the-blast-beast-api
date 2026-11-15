package com.theblastbeast.integration.ticketmaster;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.service.SlugService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Normalizador de Ticketmaster")
class TicketmasterEventNormalizerTest {

    private TicketmasterEventNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TicketmasterEventNormalizer(new SlugService());
    }

    @Test
    @DisplayName("normaliza un evento completo de España")
    void normalizaEventoCompleto() {
        TicketmasterEvent event = event("tm-1", "Metallica: M72 World Tour",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale",
                        "2026-06-21", "00:00:00"),
                venue("venue-1", "Movistar Arena", "Madrid", "ES"),
                List.of(attraction("artist-1", "Metallica")),
                List.of(classification("Music", "Metal", "Heavy Metal")),
                List.of(new TicketmasterPriceRange("standard", "EUR", 45.0, 80.0)),
                List.of(new TicketmasterImage("16_9", "https://tm.test/image.jpg", 1024, 576,
                        false)));

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.externalId()).isEqualTo("tm-1");
        assertThat(concert.title()).isEqualTo("Metallica: M72 World Tour");
        assertThat(concert.description()).isEqualTo("Info del evento");
        assertThat(concert.startAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-20T21:00:00+02:00"));
        assertThat(concert.endAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-21T00:00:00+02:00"));
        assertThat(concert.timeTba()).isFalse();
        assertThat(concert.status()).isEqualTo(ConcertStatus.ON_SALE);
        assertThat(concert.genre()).isEqualTo("Metal");
        assertThat(concert.genreSlug()).isEqualTo("metal");
        assertThat(concert.subgenre()).isEqualTo("Heavy Metal");
        assertThat(concert.ticketUrl()).isEqualTo("https://tm.test/event");
        assertThat(concert.sourceUrl()).isEqualTo("https://tm.test/event");
        assertThat(concert.priceMin()).isEqualByComparingTo(BigDecimal.valueOf(45));
        assertThat(concert.priceMax()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(concert.currency()).isEqualTo("EUR");
        assertThat(concert.imageUrl()).isEqualTo("https://tm.test/image.jpg");
        assertThat(concert.imageAttribution()).isEqualTo("Ticketmaster");
        assertThat(concert.venue().externalId()).isEqualTo("venue-1");
        assertThat(concert.venue().name()).isEqualTo("Movistar Arena");
        assertThat(concert.venue().city()).isEqualTo("Madrid");
        assertThat(concert.venue().citySlug()).isEqualTo("madrid");
        assertThat(concert.venue().region()).isEqualTo("Comunidad de Madrid");
        assertThat(concert.venue().countryCode()).isEqualTo("ES");
        assertThat(concert.venue().address()).isEqualTo("Calle de prueba 1");
        assertThat(concert.venue().latitude()).isEqualByComparingTo(BigDecimal.valueOf(40.4));
        assertThat(concert.venue().longitude()).isEqualByComparingTo(BigDecimal.valueOf(-3.7));
        assertThat(concert.artists()).singleElement().satisfies(artist -> {
            assertThat(artist.externalId()).isEqualTo("artist-1");
            assertThat(artist.name()).isEqualTo("Metallica");
            assertThat(artist.slug()).isEqualTo("metallica");
            assertThat(artist.headliner()).isTrue();
            assertThat(artist.sortOrder()).isZero();
        });
    }

    @Test
    @DisplayName("marca la hora por confirmar y usa una hora estable")
    void marcaHoraPorConfirmar() {
        TicketmasterEvent event = event("tm-2", "Concierto sin hora",
                dates("2026-06-20", null, "Europe/Madrid", "onsale", null, null),
                venue("venue-2", "Sala", "Bilbao", "ES"),
                List.of(), List.of(), List.of(), List.of());

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.timeTba()).isTrue();
        assertThat(concert.startAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-20T20:00:00+02:00"));
        assertThat(concert.endAt()).isNull();
        assertThat(concert.artists()).isEmpty();
        assertThat(concert.priceMin()).isNull();
        assertThat(concert.imageUrl()).isNull();
    }

    @Test
    @DisplayName("respeta el dateTime con offset cuando existe")
    void respetaDateTimeConOffset() {
        TicketmasterEvent event = event("tm-3", "Evento con dateTime",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null,
                        "2026-06-20T19:00:00Z", false),
                venue("venue-3", "Sala", "Madrid", "ES"),
                List.of(), List.of(), List.of(), List.of());

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.startAt()).isEqualTo(OffsetDateTime.parse("2026-06-20T19:00:00Z"));
    }

    @Test
    @DisplayName("traduce estados de Ticketmaster")
    void traduceEstados() {
        assertThat(normalizeStatus("offsale")).isEqualTo(ConcertStatus.OFF_SALE);
        assertThat(normalizeStatus("postponed")).isEqualTo(ConcertStatus.POSTPONED);
        assertThat(normalizeStatus("rescheduled")).isEqualTo(ConcertStatus.RESCHEDULED);
        assertThat(normalizeStatus("canceled")).isEqualTo(ConcertStatus.CANCELLED);
        assertThat(normalizeStatus("cancelled")).isEqualTo(ConcertStatus.CANCELLED);
        assertThat(normalizeStatus("otro")).isEqualTo(ConcertStatus.ON_SALE);
    }

    @Test
    @DisplayName("descarta eventos fuera de España, sin fecha, sin recinto o sin ciudad")
    void descartaEventosInvalidos() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize(event("", "Sin id",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null),
                venue("v", "Sala", "Madrid", "ES"), List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
        assertThat(normalizer.normalize(event("tm", "Sin recinto",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null),
                null, List.of(), List.of(), List.of(), List.of()))).isEmpty();
        assertThat(normalizer.normalize(event("tm", "Sin ciudad",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null),
                venue("v", "Sala", "", "ES"), List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
        assertThat(normalizer.normalize(event("tm", "Fuera de España",
                dates("2026-06-20", "21:00:00", "Europe/London", "onsale", null, null),
                venue("v", "O2 Academy", "Londres", "GB"), List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
        assertThat(normalizer.normalize(event("tm", "Sin fecha",
                new TicketmasterDates(null, null, "Europe/Madrid", null),
                venue("v", "Sala", "Madrid", "ES"), List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
        assertThat(normalizer.normalize(event("tm", "Fecha por anunciar",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null,
                        null, true),
                venue("v", "Sala", "Madrid", "ES"), List.of(), List.of(), List.of(),
                List.of()))).isEmpty();
    }

    @Test
    @DisplayName("usa Europe/Madrid si la zona horaria no es válida")
    void usaZonaPorDefecto() {
        TicketmasterEvent event = event("tm-4", "Zona inválida",
                dates("2026-06-20", "21:00:00", "Marte/Olympus", "onsale", null, null),
                venue("venue-4", "Sala", "Madrid", "ES"),
                List.of(), List.of(), List.of(), List.of());

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.startAt())
                .isEqualTo(OffsetDateTime.parse("2026-06-20T21:00:00+02:00"));
    }

    @Test
    @DisplayName("deduplica slugs de artistas y elige la mejor imagen")
    void deduplicaArtistasYEligeImagen() {
        TicketmasterEvent event = event("tm-5", "Festival",
                dates("2026-06-20", "12:00:00", "Europe/Madrid", "onsale", null, null),
                venue("venue-5", "Recinto", "Valencia", "ES"),
                List.of(attraction("a1", "Metallica"), attraction("a2", "Metallica")),
                List.of(),
                List.of(),
                List.of(new TicketmasterImage("4_3", "https://tm.test/primera.jpg", 100, 75,
                        false), new TicketmasterImage("16_9", "https://tm.test/segunda.jpg",
                        800, 450, false)));

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.artists()).extracting(NormalizedArtist::slug)
                .containsExactly("metallica", "metallica-2");
        assertThat(concert.imageUrl()).isEqualTo("https://tm.test/segunda.jpg");
    }

    @Test
    @DisplayName("usa pleaseNote cuando no hay info y limpia el HTML")
    void usaPleaseNoteYLimpiaHtml() {
        TicketmasterEvent event = new TicketmasterEvent("tm-6", "Evento",
                "https://tm.test/event", null, "<p>Nota <strong>importante</strong></p>",
                "es",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", "onsale", null, null),
                List.of(), List.of(), List.of(),
                new TicketmasterEventEmbedded(
                        List.of(venue("venue-6", "Sala", "Sevilla", "ES")), List.of()));

        NormalizedConcert concert = normalizer.normalize(event).orElseThrow();

        assertThat(concert.description()).isEqualTo("Nota importante");
        assertThat(concert.artists()).isEmpty();
    }

    private ConcertStatus normalizeStatus(String status) {
        return normalizer.normalize(event("tm-status", "Evento",
                dates("2026-06-20", "21:00:00", "Europe/Madrid", status, null, null),
                venue("v", "Sala", "Madrid", "ES"), List.of(), List.of(), List.of(),
                List.of())).orElseThrow().status();
    }

    private TicketmasterEvent event(String id, String name, TicketmasterDates dates,
            TicketmasterVenue venue, List<TicketmasterAttraction> attractions,
            List<TicketmasterClassification> classifications,
            List<TicketmasterPriceRange> prices, List<TicketmasterImage> images) {
        return new TicketmasterEvent(id, name, "https://tm.test/event",
                "<p>Info del evento</p>", "Nota", "es", dates, classifications, prices, images,
                new TicketmasterEventEmbedded(
                        venue == null ? List.of() : List.of(venue), attractions));
    }

    private TicketmasterDates dates(String localDate, String localTime, String timezone,
            String status, String endLocalDate, String endLocalTime) {
        return dates(localDate, localTime, timezone, status, endLocalDate, endLocalTime, null,
                false);
    }

    private TicketmasterDates dates(String localDate, String localTime, String timezone,
            String status, String endLocalDate, String endLocalTime, String dateTime,
            boolean dateTBA) {
        return new TicketmasterDates(
                new TicketmasterDate(localDate, localTime, dateTime, false, dateTBA, false,
                        false),
                endLocalDate == null
                        ? null
                        : new TicketmasterDate(endLocalDate, endLocalTime, null, false, false,
                                false, false),
                timezone,
                status == null ? null : new TicketmasterStatus(status));
    }

    private TicketmasterVenue venue(String id, String name, String city, String countryCode) {
        return new TicketmasterVenue(id, name, "es", "Europe/Madrid",
                new TicketmasterCity(city),
                new TicketmasterState("Comunidad de Madrid", "MD"),
                new TicketmasterCountry("España", countryCode),
                new TicketmasterAddress("Calle de prueba 1"),
                new TicketmasterLocation("-3.7", "40.4"));
    }

    private TicketmasterAttraction attraction(String id, String name) {
        return new TicketmasterAttraction(id, name, "https://tm.test/artist");
    }

    private TicketmasterClassification classification(String segment, String genre,
            String subgenre) {
        return new TicketmasterClassification(true,
                new TicketmasterNamed("segment", segment),
                new TicketmasterNamed("genre", genre),
                new TicketmasterNamed("subgenre", subgenre),
                null, null);
    }
}
