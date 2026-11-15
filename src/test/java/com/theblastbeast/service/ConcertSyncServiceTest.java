package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.ConcertStatus;
import com.theblastbeast.domain.entity.ConcertSyncRun;
import com.theblastbeast.domain.entity.ConcertSyncStatus;
import com.theblastbeast.domain.vo.ConcertSyncResult;
import com.theblastbeast.integration.ticketmaster.NormalizedConcert;
import com.theblastbeast.integration.ticketmaster.NormalizedVenue;
import com.theblastbeast.integration.ticketmaster.TicketmasterClient;
import com.theblastbeast.integration.ticketmaster.TicketmasterEvent;
import com.theblastbeast.integration.ticketmaster.TicketmasterEventNormalizer;
import com.theblastbeast.integration.ticketmaster.TicketmasterEventPage;
import com.theblastbeast.integration.ticketmaster.TicketmasterException;
import com.theblastbeast.integration.ticketmaster.TicketmasterQuery;
import com.theblastbeast.properties.TicketmasterProperties;
import com.theblastbeast.repository.ConcertSyncRunRepository;
import com.theblastbeast.util.MadridTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Sincronización de la agenda")
class ConcertSyncServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    @Mock
    private TicketmasterClient client;

    @Mock
    private TicketmasterEventNormalizer normalizer;

    @Mock
    private ConcertImportService importService;

    @Mock
    private ConcertSyncRunRepository runRepository;

    private ConcertSyncService service;

    @Test
    @DisplayName("omite la sincronización si no hay clave")
    void omiteSinClave() {
        service = service(properties(false, "", 14));

        ConcertSyncResult result = service.sync();

        assertThat(result.getStatus()).isEqualTo(ConcertSyncResult.StatusEnum.SKIPPED);
        assertThat(result.getMessage()).contains("TICKETMASTER_API_KEY");
        verifyNoInteractions(client, normalizer, importService);
    }

    @Test
    @DisplayName("recorre las ventanas, importa y archiva lo que ya no existe")
    void sincronizaVentanasYArchiva() {
        stubRunRepository();
        service = service(properties(true, "key", 14));
        when(client.search(any())).thenAnswer(invocation -> {
            TicketmasterQuery query = invocation.getArgument(0);
            String id = query.from().equals(TODAY) ? "evt-1" : "evt-2";
            return page(1, event(id));
        });
        when(normalizer.normalize(any())).thenAnswer(invocation -> {
            TicketmasterEvent event = invocation.getArgument(0);
            return Optional.of(normalized(event.id()));
        });
        when(importService.importEvents(anyList(), any()))
                .thenReturn(new ConcertImportService.ImportStats(2, 0));
        when(importService.archiveNotSeen(any(), any(), any())).thenReturn(1);

        ConcertSyncResult result = service.sync();

        assertThat(result.getStatus()).isEqualTo(ConcertSyncResult.StatusEnum.SUCCESS);
        assertThat(result.getFetched()).isEqualTo(2);
        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getArchived()).isEqualTo(1);
        assertThat(result.getStartedAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-14T10:00:00Z"));
        verify(client, times(2)).search(any());
        verify(importService).importEvents(
                org.mockito.ArgumentMatchers.argThat(events -> events.size() == 2),
                eq(OffsetDateTime.parse("2026-11-14T10:00:00Z")));
        verify(importService).archiveNotSeen(
                eq(OffsetDateTime.parse("2026-11-14T10:00:00Z")),
                eq(OffsetDateTime.parse("2026-11-14T10:00:00Z")),
                eq(MadridTime.startOfDay(LocalDate.of(2026, 11, 28))));

        ArgumentCaptor<ConcertSyncRun> runs = ArgumentCaptor.forClass(ConcertSyncRun.class);
        verify(runRepository, times(2)).save(runs.capture());
        assertThat(runs.getAllValues().get(1).getStatus()).isEqualTo(ConcertSyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("divide una ventana cuando supera el límite de paginación")
    void divideVentanaSiSuperaPaginacion() {
        stubRunRepository();
        service = service(properties(true, "key", 7));
        when(client.search(any())).thenAnswer(invocation -> {
            TicketmasterQuery query = invocation.getArgument(0);
            if (query.from().equals(TODAY) && query.to().equals(TODAY.plusDays(7))) {
                return new TicketmasterEventPage(List.of(), 0, 10, 2000);
            }
            return page(1, event("evt-" + query.from()));
        });
        when(normalizer.normalize(any())).thenAnswer(invocation -> {
            TicketmasterEvent event = invocation.getArgument(0);
            return Optional.of(normalized(event.id()));
        });
        when(importService.importEvents(anyList(), any()))
                .thenReturn(new ConcertImportService.ImportStats(2, 0));
        when(importService.archiveNotSeen(any(), any(), any())).thenReturn(0);

        ConcertSyncResult result = service.sync();

        assertThat(result.getStatus()).isEqualTo(ConcertSyncResult.StatusEnum.SUCCESS);
        assertThat(result.getFetched()).isEqualTo(2);
        verify(client, times(3)).search(any());
    }

    @Test
    @DisplayName("registra el fallo y no archiva conciertos")
    void registraFallo() {
        stubRunRepository();
        service = service(properties(true, "key", 7));
        when(client.search(any())).thenThrow(new TicketmasterException("boom"));

        ConcertSyncResult result = service.sync();

        assertThat(result.getStatus()).isEqualTo(ConcertSyncResult.StatusEnum.FAILED);
        assertThat(result.getMessage()).isEqualTo("boom");
        verify(importService, never()).importEvents(anyList(), any());
        verify(importService, never()).archiveNotSeen(any(), any(), any());

        ArgumentCaptor<ConcertSyncRun> runs = ArgumentCaptor.forClass(ConcertSyncRun.class);
        verify(runRepository, times(2)).save(runs.capture());
        assertThat(runs.getAllValues().get(1).getStatus()).isEqualTo(ConcertSyncStatus.FAILED);
        assertThat(runs.getAllValues().get(1).getErrorMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("una sincronización sin eventos termina correctamente")
    void sinEventos() {
        stubRunRepository();
        service = service(properties(true, "key", 7));
        when(client.search(any())).thenReturn(new TicketmasterEventPage(List.of(), 0, 0, 0));
        when(importService.importEvents(anyList(), any()))
                .thenReturn(new ConcertImportService.ImportStats(0, 0));
        when(importService.archiveNotSeen(any(), any(), any())).thenReturn(0);

        ConcertSyncResult result = service.sync();

        assertThat(result.getStatus()).isEqualTo(ConcertSyncResult.StatusEnum.SUCCESS);
        assertThat(result.getFetched()).isZero();
    }

    private ConcertSyncService service(TicketmasterProperties properties) {
        return new ConcertSyncService(properties, client, normalizer, importService,
                runRepository, CLOCK);
    }

    private void stubRunRepository() {
        when(runRepository.save(any(ConcertSyncRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TicketmasterProperties properties(boolean enabled, String apiKey, int syncDays) {
        return new TicketmasterProperties(enabled, apiKey, "https://tm.test", "ES", "es",
                "0 0 */6 * * *", syncDays, 7, 200, 5, 3, Duration.ZERO);
    }

    private TicketmasterEventPage page(int totalPages, TicketmasterEvent event) {
        return new TicketmasterEventPage(List.of(event), 0, totalPages, 1);
    }

    private TicketmasterEvent event(String id) {
        return new TicketmasterEvent(id, "Concierto", null, null, null, null, null, null,
                null, null, null);
    }

    private NormalizedConcert normalized(String externalId) {
        return new NormalizedConcert(
                externalId,
                "Concierto " + externalId,
                null,
                MadridTime.startOfDay(TODAY.plusDays(10)).plusHours(21),
                null,
                false,
                ConcertStatus.ON_SALE,
                "Metal",
                "metal",
                "Heavy Metal",
                null,
                null,
                null,
                null,
                "EUR",
                null,
                null,
                new NormalizedVenue("venue-1", "Sala", "Madrid", "madrid", "Madrid", "ES",
                        null, null, null, "Europe/Madrid"),
                List.of());
    }
}
