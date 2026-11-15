package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Release;
import com.theblastbeast.domain.entity.ReleaseKind;
import com.theblastbeast.domain.vo.ReleaseAdminResponse;
import com.theblastbeast.domain.vo.ReleaseFacet;
import com.theblastbeast.domain.vo.ReleasePage;
import com.theblastbeast.domain.vo.ReleaseType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("Radar de lanzamientos")
class ReleaseIT extends AbstractIntegrationTest {

    private Band metallica;
    private YearMonth currentMonth;

    @BeforeEach
    void seedReleases() {
        seedAdmin();
        metallica = seedBand("metallica", "Metallica");
        currentMonth = YearMonth.now(Clock.systemUTC());
        LocalDate first = currentMonth.atDay(5);

        seedRelease("metallica-forge", "The Forge of Fury", "Metallica", metallica,
                ReleaseKind.ALBUM, first, "Thrash metal");
        seedRelease("gojira-silent-tide", "The Silent Tide", "Gojira", null,
                ReleaseKind.SINGLE, first.plusDays(2), "Progressive metal");

        Release hidden = seedRelease("opeth-secreto", "Disco secreto", "Opeth", null,
                ReleaseKind.ALBUM, first.plusDays(4), "Progressive metal");
        hidden.setHidden(true);
        releaseRepository.saveAndFlush(hidden);

        seedRelease("judas-shield", "Shield of the North", "Judas Priest", null,
                ReleaseKind.ALBUM, currentMonth.plusMonths(1).atDay(3), "Heavy metal");
    }

    @Test
    @DisplayName("el listado público filtra por mes, género y tipo")
    void listadoPublicoConFacetas() {
        ReleasePage page = list("/api/v1/releases?month=" + currentMonth);

        assertThat(page.getMonth()).isEqualTo(currentMonth.toString());
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting("slug")
                .containsExactly("metallica-forge", "gojira-silent-tide");
        assertThat(page.getGenres()).extracting(ReleaseFacet::getValue)
                .containsExactlyInAnyOrder("thrash-metal", "progressive-metal");
        assertThat(page.getTypes()).extracting(ReleaseFacet::getValue)
                .containsExactlyInAnyOrder("album", "single");

        ReleasePage filtered = list("/api/v1/releases?month=" + currentMonth
                + "&genre=thrash-metal&type=album");
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent().get(0).getTitle()).isEqualTo("The Forge of Fury");
        assertThat(filtered.getContent().get(0).getType()).isEqualTo(ReleaseType.ALBUM);
        assertThat(filtered.getContent().get(0).getBand().getSlug()).isEqualTo("metallica");
    }

    @Test
    @DisplayName("un mes con formato inválido devuelve 400")
    void mesInvalido() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity(
                "/api/v1/releases?month=2026-13", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("code", "GEN-001");
    }

    @Test
    @DisplayName("el calendario iCalendar incluye los lanzamientos visibles")
    void calendarioICS() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/releases/calendar.ics?months=3", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(
                MediaType.valueOf("text/calendar"))).isTrue();
        String calendar = response.getBody();
        assertThat(calendar).contains("BEGIN:VCALENDAR");
        assertThat(calendar).contains("VERSION:2.0");
        assertThat(calendar).contains("BEGIN:VEVENT");
        assertThat(calendar).contains("DTSTART;VALUE=DATE:");
        assertThat(calendar).contains("SUMMARY:Metallica — The Forge of Fury (Álbum)");
        assertThat(calendar).doesNotContain("Disco secreto");
        assertThat(calendar).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    @DisplayName("la redacción crea, lista, actualiza y elimina lanzamientos")
    void crudEditorial() {
        String token = loginAsAdmin();

        ReleaseAdminResponse created = create(token, Map.of(
                "title", "Decades of Silence",
                "artistName", "Nightwish",
                "bandId", metallica.getId(),
                "type", "compilation",
                "releaseDate", currentMonth.atDay(20).toString(),
                "genre", "Symphonic metal",
                "label", "Nuclear Blast",
                "featured", true));
        assertThat(created.getSlug()).isEqualTo("nightwish-decades-of-silence-"
                + currentMonth.getYear());
        assertThat(created.getType()).isEqualTo(ReleaseType.COMPILATION);
        assertThat(created.getBandId()).isEqualTo(metallica.getId());
        assertThat(created.getFeatured()).isTrue();

        ResponseEntity<List<ReleaseAdminResponse>> list = rest.exchange(
                "/api/v1/admin/releases?q=nightwish",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);

        ResponseEntity<ReleaseAdminResponse> updated = rest.exchange(
                "/api/v1/admin/releases/" + created.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "title", "Decades of Silence (Deluxe)",
                        "artistName", "Nightwish",
                        "type", "compilation",
                        "releaseDate", currentMonth.atDay(21).toString(),
                        "hidden", true), jsonHeaders(token)),
                ReleaseAdminResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getTitle()).isEqualTo("Decades of Silence (Deluxe)");
        assertThat(updated.getBody().getHidden()).isTrue();
        assertThat(updated.getBody().getBand()).isNull();

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/releases/" + created.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(releaseRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("el panel exige autenticación y valida banda y lanzamiento")
    void validacionesEditoriales() {
        assertThat(rest.getForEntity("/api/v1/admin/releases", ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = loginAsAdmin();
        ResponseEntity<ProblemDetail> missingBand = rest.exchange(
                "/api/v1/admin/releases",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "title", "Sin banda",
                        "artistName", "Nadie",
                        "bandId", 9999,
                        "type", "album",
                        "releaseDate", currentMonth.atDay(10).toString()),
                        jsonHeaders(token)),
                ProblemDetail.class);
        assertThat(missingBand.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingBand.getBody().getProperties()).containsEntry("code", "REL-002");

        ResponseEntity<ProblemDetail> missingRelease = rest.exchange(
                "/api/v1/admin/releases/9999",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "title", "No existe",
                        "artistName", "Nadie",
                        "type", "album",
                        "releaseDate", currentMonth.atDay(10).toString()),
                        jsonHeaders(token)),
                ProblemDetail.class);
        assertThat(missingRelease.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingRelease.getBody().getProperties()).containsEntry("code", "REL-001");
    }

    private ReleasePage list(String url) {
        ResponseEntity<ReleasePage> response = rest.getForEntity(url, ReleasePage.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ReleaseAdminResponse create(String token, Map<String, Object> body) {
        HttpHeaders headers = jsonHeaders(token);
        ResponseEntity<ReleaseAdminResponse> response = rest.exchange(
                "/api/v1/admin/releases",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                ReleaseAdminResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
