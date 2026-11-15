package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.integration.ticketmaster.NormalizedArtist;
import com.theblastbeast.integration.ticketmaster.NormalizedConcert;
import com.theblastbeast.integration.ticketmaster.NormalizedVenue;
import com.theblastbeast.service.ConcertImportService;
import com.theblastbeast.util.MadridTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Importación de la agenda")
class ConcertImportIT extends AbstractIntegrationTest {

    @Autowired
    private ConcertImportService concertImportService;

    @BeforeEach
    void seedBand() {
        seedBand("metallica", "Metallica");
    }

    @Test
    @DisplayName("crea, actualiza y archiva conciertos de forma idempotente")
    void creaActualizaYArchiva() {
        OffsetDateTime now = OffsetDateTime.now(Clock.systemUTC());
        ConcertImportService.ImportStats created = concertImportService.importEvents(
                List.of(event("tm-1", "Metallica en Madrid", "Metallica", "metallica")), now);

        assertThat(created.created()).isEqualTo(1);
        Concert first = concertRepository.findBySourceAndExternalId("TICKETMASTER", "tm-1")
                .orElseThrow();
        String slug = first.getSlug();
        assertThat(slug).startsWith("metallica-en-madrid-");
        assertThat(first.getVenue().getCitySlug()).isEqualTo("madrid");
        assertThat(first.getArtists()).singleElement().satisfies(artist -> {
            assertThat(artist.getSlug()).isEqualTo("metallica");
            assertThat(artist.getBand()).isNotNull();
            assertThat(artist.getBand().getSlug()).isEqualTo("metallica");
        });

        ConcertImportService.ImportStats updated = concertImportService.importEvents(
                List.of(event("tm-1", "Metallica en Madrid (nueva fecha)", "Metallica",
                        "metallica")), now.plusHours(1));
        assertThat(updated.updated()).isEqualTo(1);
        Concert reloaded = concertRepository.findBySourceAndExternalId("TICKETMASTER", "tm-1")
                .orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Metallica en Madrid (nueva fecha)");
        assertThat(reloaded.getSlug()).isEqualTo(slug);

        concertImportService.importEvents(
                List.of(event("tm-2", "Metallica en Madrid", "Ghost", "ghost")),
                now.plusHours(1));
        Concert second = concertRepository.findBySourceAndExternalId("TICKETMASTER", "tm-2")
                .orElseThrow();
        assertThat(second.getSlug()).isEqualTo(slug + "-2");
        assertThat(second.getArtists()).singleElement()
                .satisfies(artist -> assertThat(artist.getBand()).isNull());

        int archived = concertImportService.archiveNotSeen(
                now.plusHours(2), now.plusHours(2), now.plusDays(30));

        assertThat(archived).isEqualTo(2);
        assertThat(concertRepository.findBySourceAndExternalId("TICKETMASTER", "tm-1")
                .orElseThrow().getStatus()).isEqualTo(ConcertStatus.ARCHIVED);
        assertThat(concertRepository.findBySourceAndExternalId("TICKETMASTER", "tm-2")
                .orElseThrow().getStatus()).isEqualTo(ConcertStatus.ARCHIVED);
    }

    private NormalizedConcert event(String externalId, String title, String artistName,
            String artistSlug) {
        OffsetDateTime startAt = MadridTime.startOfDay(
                        MadridTime.today(Clock.systemUTC()).plusDays(10))
                .plusHours(21);
        return new NormalizedConcert(
                externalId,
                title,
                "Descripción del concierto",
                startAt,
                startAt.plusHours(3),
                false,
                ConcertStatus.ON_SALE,
                "Metal",
                "metal",
                "Heavy Metal",
                "https://tm.test/tickets",
                "https://tm.test/event",
                BigDecimal.valueOf(45),
                BigDecimal.valueOf(80),
                "EUR",
                null,
                null,
                new NormalizedVenue("venue-1", "Movistar Arena", "Madrid", "madrid", "Madrid",
                        "ES", "Calle de prueba 1", BigDecimal.valueOf(40.4),
                        BigDecimal.valueOf(-3.7), "Europe/Madrid"),
                List.of(new NormalizedArtist("artist-" + artistSlug, artistName, artistSlug,
                        true, 0)));
    }
}
