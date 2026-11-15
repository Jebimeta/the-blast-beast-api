package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.Concert;
import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.Venue;
import com.theblastbeast.domain.vo.ConcertDetail;
import com.theblastbeast.domain.vo.ConcertFacets;
import com.theblastbeast.domain.vo.ConcertPage;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ConcertMapper;
import com.theblastbeast.repository.ConcertDayCountView;
import com.theblastbeast.repository.ConcertDateRangeView;
import com.theblastbeast.repository.ConcertFilter;
import com.theblastbeast.repository.ConcertRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("Servicio de la agenda")
class ConcertServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ConcertRepository concertRepository;

    private ConcertService concertService;

    @BeforeEach
    void setUp() {
        concertService = new ConcertService(concertRepository, new ConcertMapper(), CLOCK);
    }

    @Test
    @DisplayName("pagina y mapea los conciertos")
    void paginaYMapea() {
        Concert concert = concert();
        when(concertRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(concert), PageRequest.of(0, 20), 1));

        ConcertPage page = concertService.list(
                new ConcertFilter(null, null, null, null, null), 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getTitle()).isEqualTo("Metallica en Madrid");
            assertThat(summary.getGenre()).isEqualTo("metal");
            assertThat(summary.getGenreLabel()).isEqualTo("Metal");
            assertThat(summary.getVenue().getCitySlug()).isEqualTo("madrid");
            assertThat(summary.getStatus())
                    .isEqualTo(com.theblastbeast.domain.vo.ConcertStatus.ON_SALE);
        });
    }

    @Test
    @DisplayName("rechaza un rango de fechas invertido antes de consultar")
    void rechazaRangoInvertido() {
        ConcertFilter filter = new ConcertFilter(null, null, null,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> concertService.list(filter, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("devuelve el detalle y responde 404 si no existe o está oculto")
    void detalle() {
        Concert concert = concert();
        when(concertRepository.findBySlugWithDetails("metallica-madrid"))
                .thenReturn(Optional.of(concert));

        ConcertDetail detail = concertService.detail("metallica-madrid");

        assertThat(detail.getTitle()).isEqualTo("Metallica en Madrid");
        assertThat(detail.getVenue().getCitySlug()).isEqualTo("madrid");

        concert.setHidden(true);
        assertThatThrownBy(() -> concertService.detail("metallica-madrid"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.CONCERT_NOT_FOUND));
    }

    @Test
    @DisplayName("mapea facetas y el rango de fechas")
    void facetas() {
        when(concertRepository.countByGenre(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"metal", "Metal", 2L}));
        when(concertRepository.countByCity(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"madrid", "Madrid", 2L}));
        when(concertRepository.countByArtist(any(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"metallica", "Metallica", 1L}));
        when(concertRepository.countByDay(any(), any()))
                .thenReturn(List.of(dayCount()));
        when(concertRepository.findDateRange(any(), any()))
                .thenReturn(new ConcertDateRangeView() {
                    @Override
                    public OffsetDateTime getEarliest() {
                        return OffsetDateTime.parse("2026-11-20T20:00:00Z");
                    }

                    @Override
                    public OffsetDateTime getLatest() {
                        return OffsetDateTime.parse("2026-12-01T20:00:00Z");
                    }
                });

        ConcertFacets facets = concertService.facets();

        assertThat(facets.getGenres()).singleElement().satisfies(value -> {
            assertThat(value.getValue()).isEqualTo("metal");
            assertThat(value.getLabel()).isEqualTo("Metal");
            assertThat(value.getCount()).isEqualTo(2);
        });
        assertThat(facets.getCities()).singleElement()
                .satisfies(value -> assertThat(value.getValue()).isEqualTo("madrid"));
        assertThat(facets.getArtists()).singleElement()
                .satisfies(value -> assertThat(value.getValue()).isEqualTo("metallica"));
        assertThat(facets.getDays()).singleElement().satisfies(day -> {
            assertThat(day.getDate()).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(day.getCount()).isEqualTo(3);
        });
        assertThat(facets.getEarliest()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(facets.getLatest()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("tolera un rango de fechas vacío en las facetas")
    void facetasSinRango() {
        when(concertRepository.countByGenre(any(), any())).thenReturn(List.of());
        when(concertRepository.countByCity(any(), any())).thenReturn(List.of());
        when(concertRepository.countByArtist(any(), any(), any())).thenReturn(List.of());
        when(concertRepository.countByDay(any(), any())).thenReturn(List.of());
        when(concertRepository.findDateRange(any(), any()))
                .thenReturn(new ConcertDateRangeView() {
                    @Override
                    public OffsetDateTime getEarliest() {
                        return null;
                    }

                    @Override
                    public OffsetDateTime getLatest() {
                        return null;
                    }
                });

        ConcertFacets facets = concertService.facets();

        assertThat(facets.getEarliest()).isNull();
        assertThat(facets.getLatest()).isNull();
        assertThat(facets.getGenres()).isEmpty();
    }

    private Concert concert() {
        Venue venue = new Venue();
        venue.setId(1L);
        venue.setName("Movistar Arena");
        venue.setCity("Madrid");
        venue.setCitySlug("madrid");
        venue.setRegion("Madrid");

        Concert concert = new Concert();
        concert.setId(1L);
        concert.setSlug("metallica-madrid");
        concert.setTitle("Metallica en Madrid");
        concert.setStartAt(OffsetDateTime.parse("2026-11-24T20:00:00Z"));
        concert.setStatus(ConcertStatus.ON_SALE);
        concert.setGenre("Metal");
        concert.setGenreSlug("metal");
        concert.setVenue(venue);
        concert.setHidden(false);
        concert.setFeatured(false);
        return concert;
    }

    private ConcertDayCountView dayCount() {
        return new ConcertDayCountView() {
            @Override
            public LocalDate getDay() {
                return LocalDate.of(2026, 11, 20);
            }

            @Override
            public long getTotal() {
                return 3;
            }
        };
    }
}
