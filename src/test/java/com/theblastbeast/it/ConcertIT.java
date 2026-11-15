package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertArtist;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.domain.vo.ConcertDetail;
import com.theblastbeast.domain.vo.ConcertFacets;
import com.theblastbeast.domain.vo.ConcertPage;
import com.theblastbeast.domain.vo.ConcertSummary;
import com.theblastbeast.domain.vo.ConcertSyncResult;
import com.theblastbeast.domain.vo.FacetValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Agenda de conciertos")
class ConcertIT extends AbstractIntegrationTest {

    private Concert madridMetal;
    private Concert hiddenConcert;

    @BeforeEach
    void seedAgenda() {
        Band metallica = seedBand("metallica", "Metallica");
        Venue madrid = seedVenue("tm-venue-madrid", "Movistar Arena", "Madrid");
        Venue barcelona = seedVenue("tm-venue-barcelona", "Palau Sant Jordi", "Barcelona");

        OffsetDateTime now = OffsetDateTime.now();
        madridMetal = seedConcert("tm-event-1", "metallica-madrid", "Metallica en Madrid",
                madrid, now.plusDays(10), ConcertStatus.ON_SALE, "metal", "Metal", false,
                metallica);
        seedConcert("tm-event-2", "ghost-barcelona", "Ghost en Barcelona",
                barcelona, now.plusDays(20), ConcertStatus.ON_SALE, "rock", "Rock", false, null);
        hiddenConcert = seedConcert("tm-event-3", "oculto-madrid", "Concierto oculto",
                madrid, now.plusDays(5), ConcertStatus.ON_SALE, "metal", "Metal", true, null);
        seedConcert("tm-event-4", "cancelado-madrid", "Concierto cancelado",
                madrid, now.plusDays(7), ConcertStatus.CANCELLED, "metal", "Metal", false, null);
    }

    @Test
    @DisplayName("solo lista conciertos visibles y futuros, ordenados por fecha")
    void listaConciertosVisibles() {
        ConcertPage page = list("/api/v1/concerts");

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ConcertSummary::getTitle)
                .containsExactly("Metallica en Madrid", "Ghost en Barcelona");
        assertThat(page.getContent().get(0).getVenue().getCitySlug()).isEqualTo("madrid");
        assertThat(page.getContent().get(0).getArtists()).anySatisfy(artist ->
                assertThat(artist.getBandSlug()).isEqualTo("metallica"));
        assertThat(page.getFirst()).isTrue();
        assertThat(page.getLast()).isTrue();
    }

    @Test
    @DisplayName("filtra por género, ciudad, banda y rango de fechas")
    void filtraPorGeneroCiudadBandaYFecha() {
        assertThat(list("/api/v1/concerts?genre=metal").getTotalElements()).isEqualTo(1);
        assertThat(list("/api/v1/concerts?city=barcelona").getTotalElements()).isEqualTo(1);
        assertThat(list("/api/v1/concerts?band=metallica").getTotalElements()).isEqualTo(1);
        assertThat(list("/api/v1/concerts?band=Metallica").getTotalElements()).isEqualTo(1);

        LocalDate from = LocalDate.now().plusDays(15);
        LocalDate to = LocalDate.now().plusDays(25);
        assertThat(list("/api/v1/concerts?from=" + from + "&to=" + to).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza un rango de fechas invertido")
    void rechazaRangoInvertido() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity(
                "/api/v1/concerts?from=2026-12-31&to=2026-01-01", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("devuelve el detalle y oculta los conciertos no publicados")
    void detalleYNoEncontrado() {
        ConcertDetail detail = rest.getForObject(
                "/api/v1/concerts/" + madridMetal.getSlug(), ConcertDetail.class);

        assertThat(detail).isNotNull();
        assertThat(detail.getTitle()).isEqualTo("Metallica en Madrid");
        assertThat(detail.getVenue().getCitySlug()).isEqualTo("madrid");
        assertThat(detail.getArtists()).anySatisfy(artist ->
                assertThat(artist.getBandSlug()).isEqualTo("metallica"));
        assertThat(detail.getSource()).isEqualTo("Ticketmaster");

        ResponseEntity<ProblemDetail> hidden = rest.getForEntity(
                "/api/v1/concerts/" + hiddenConcert.getSlug(), ProblemDetail.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("expone facetas de géneros, ciudades, bandas y días")
    void exponeFacetas() {
        ConcertFacets facets = rest.getForObject("/api/v1/concerts/facets", ConcertFacets.class);

        assertThat(facets).isNotNull();
        assertThat(facets.getGenres()).extracting(FacetValue::getValue)
                .contains("metal", "rock");
        assertThat(facets.getCities()).extracting(FacetValue::getValue)
                .contains("madrid", "barcelona");
        assertThat(facets.getArtists()).extracting(FacetValue::getValue).contains("metallica");
        assertThat(facets.getDays()).isNotEmpty();
        assertThat(facets.getEarliest()).isNotNull().isAfter(LocalDate.now());
        assertThat(facets.getLatest()).isNotNull().isAfter(facets.getEarliest());
    }

    @Test
    @DisplayName("la sincronización manual se omite si Ticketmaster no está configurado")
    void sincronizacionOmitidaSinClave() {
        seedAdmin();
        String token = loginAsAdmin();

        ResponseEntity<ConcertSyncResult> response = rest.exchange(
                "/api/v1/admin/concerts/sync",
                HttpMethod.POST,
                new HttpEntity<>(jsonHeaders(token)),
                ConcertSyncResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus())
                .isEqualTo(ConcertSyncResult.StatusEnum.SKIPPED);
    }

    @Test
    @DisplayName("la sincronización manual exige autenticación")
    void sincronizacionExigeAutenticacion() {
        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/admin/concerts/sync",
                HttpMethod.POST,
                HttpEntity.EMPTY,
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ConcertPage list(String url) {
        ResponseEntity<ConcertPage> response = rest.getForEntity(url, ConcertPage.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Venue seedVenue(String externalId, String name, String city) {
        Venue venue = new Venue();
        venue.setSource("TICKETMASTER");
        venue.setExternalId(externalId);
        venue.setName(name);
        venue.setCity(city);
        venue.setCitySlug(city.toLowerCase(Locale.ROOT).replace(' ', '-'));
        venue.setRegion("España");
        venue.setCountryCode("ES");
        venue.setTimezone("Europe/Madrid");
        return venueRepository.saveAndFlush(venue);
    }

    private Concert seedConcert(String externalId, String slug, String title, Venue venue,
            OffsetDateTime startAt, ConcertStatus status, String genreSlug, String genre,
            boolean hidden, Band band) {
        Concert concert = new Concert();
        concert.setSource("TICKETMASTER");
        concert.setExternalId(externalId);
        concert.setSlug(slug);
        concert.setTitle(title);
        concert.setStartAt(startAt);
        concert.setEndAt(startAt.plusHours(2));
        concert.setStatus(status);
        concert.setGenre(genre);
        concert.setGenreSlug(genreSlug);
        concert.setVenue(venue);
        concert.setHidden(hidden);
        concert.setFeatured(false);
        concert.setLastSeenAt(OffsetDateTime.now());
        concert.setCurrency("EUR");
        concert.setPriceMin(BigDecimal.valueOf(50));
        concert.setPriceMax(BigDecimal.valueOf(80));
        concert.setTicketUrl("https://example.test/tickets");
        if (band != null) {
            ConcertArtist artist = new ConcertArtist();
            artist.setName(band.getName());
            artist.setSlug(band.getSlug());
            artist.setHeadliner(true);
            artist.setSortOrder(0);
            artist.setBand(band);
            concert.addArtist(artist);
        }
        return concertRepository.saveAndFlush(concert);
    }
}
