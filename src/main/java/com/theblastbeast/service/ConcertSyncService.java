package com.theblastbeast.service;

import com.theblastbeast.domain.entity.ConcertSyncRun;
import com.theblastbeast.domain.entity.ConcertSyncStatus;
import com.theblastbeast.domain.vo.ConcertSyncResult;
import com.theblastbeast.integration.ticketmaster.NormalizedConcert;
import com.theblastbeast.integration.ticketmaster.TicketmasterClient;
import com.theblastbeast.integration.ticketmaster.TicketmasterEvent;
import com.theblastbeast.integration.ticketmaster.TicketmasterEventNormalizer;
import com.theblastbeast.integration.ticketmaster.TicketmasterEventPage;
import com.theblastbeast.integration.ticketmaster.TicketmasterQuery;
import com.theblastbeast.properties.TicketmasterProperties;
import com.theblastbeast.repository.ConcertSyncRunRepository;
import com.theblastbeast.util.MadridTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sincroniza la agenda con Ticketmaster en ventanas de fechas.
 *
 * <p>Ticketmaster limita la paginación profunda a 1.000 resultados por consulta.
 * Cuando una ventana devuelve más páginas de las permitidas se parte en dos
 * ventanas más pequeñas y se repite la consulta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertSyncService {

    private static final int MAX_TICKETMASTER_PAGE = 200;
    private static final int DEEP_PAGING_LIMIT = 1000;
    private static final int MAX_SPLIT_DEPTH = 10;

    private final TicketmasterProperties properties;
    private final TicketmasterClient ticketmasterClient;
    private final TicketmasterEventNormalizer normalizer;
    private final ConcertImportService importService;
    private final ConcertSyncRunRepository runRepository;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public ConcertSyncResult sync() {
        if (!properties.enabled() || !StringUtils.hasText(properties.apiKey())) {
            return skipped("Ticketmaster no está configurado: define TICKETMASTER_API_KEY "
                    + "y TICKETMASTER_ENABLED=true");
        }
        if (!running.compareAndSet(false, true)) {
            return skipped("Ya hay una sincronización de la agenda en curso");
        }

        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        ConcertSyncRun run = new ConcertSyncRun();
        run.setStartedAt(startedAt);
        run.setStatus(ConcertSyncStatus.RUNNING);
        try {
            run = runRepository.save(run);
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }

        try {
            SyncAccumulator accumulator = new SyncAccumulator();
            LocalDate today = MadridTime.today(clock);
            LocalDate end = today.plusDays(Math.max(properties.syncDays(), 1));
            LocalDate cursor = today;
            while (cursor.isBefore(end)) {
                LocalDate windowEnd = cursor.plusDays(Math.max(properties.windowDays(), 1));
                if (windowEnd.isAfter(end)) {
                    windowEnd = end;
                }
                fetchWindow(cursor, windowEnd, 0, accumulator);
                cursor = windowEnd;
            }

            OffsetDateTime finishedAt = OffsetDateTime.now(clock);
            ConcertImportService.ImportStats stats =
                    importService.importEvents(accumulator.events(), startedAt);
            int archived = importService.archiveNotSeen(
                    startedAt, finishedAt, MadridTime.startOfDay(end));

            run.setFetched(accumulator.events().size());
            run.setCreated(stats.created());
            run.setUpdated(stats.updated());
            run.setArchived(archived);
            run.setStatus(ConcertSyncStatus.SUCCESS);
            run.setFinishedAt(finishedAt);
            runRepository.save(run);

            log.info("Sincronización de la agenda completada: {} eventos, {} nuevos, {} actualizados, "
                            + "{} archivados",
                    accumulator.events().size(), stats.created(), stats.updated(), archived);
            return result(ConcertSyncResult.StatusEnum.SUCCESS, accumulator.events().size(),
                    stats.created(), stats.updated(), archived, startedAt, finishedAt,
                    "Sincronización completada");
        } catch (RuntimeException exception) {
            log.error("Fallo al sincronizar la agenda de Ticketmaster", exception);
            run.setStatus(ConcertSyncStatus.FAILED);
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setErrorMessage(truncate(exception.getMessage(), 500));
            runRepository.save(run);
            return result(ConcertSyncResult.StatusEnum.FAILED, run.getFetched(), run.getCreated(),
                    run.getUpdated(), run.getArchived(), startedAt, run.getFinishedAt(),
                    truncate(exception.getMessage(), 500));
        } finally {
            running.set(false);
        }
    }

    private void fetchWindow(LocalDate from, LocalDate to, int depth, SyncAccumulator accumulator) {
        int size = Math.min(Math.max(properties.pageSize(), 1), MAX_TICKETMASTER_PAGE);
        int maxPages = Math.min(Math.max(properties.maxPagesPerWindow(), 1),
                Math.max(DEEP_PAGING_LIMIT / size, 1));

        TicketmasterEventPage first = ticketmasterClient.search(
                new TicketmasterQuery(from, to, 0, size));
        long days = ChronoUnit.DAYS.between(from, to);

        if (first.totalPages() > maxPages && days > 1 && depth < MAX_SPLIT_DEPTH) {
            LocalDate middle = from.plusDays(days / 2);
            log.debug("Ventana {}..{} con {} páginas; se divide en {}..{} y {}..{}",
                    from, to, first.totalPages(), from, middle, middle, to);
            fetchWindow(from, middle, depth + 1, accumulator);
            fetchWindow(middle, to, depth + 1, accumulator);
            return;
        }

        addEvents(first, accumulator);
        int pages = Math.min(first.totalPages(), maxPages);
        for (int page = 1; page < pages; page++) {
            addEvents(ticketmasterClient.search(new TicketmasterQuery(from, to, page, size)),
                    accumulator);
        }
        if (first.totalPages() > maxPages) {
            log.warn("La ventana {}..{} supera el límite de paginación de Ticketmaster; "
                    + "se importan {} de {} páginas", from, to, maxPages, first.totalPages());
        }
    }

    private void addEvents(TicketmasterEventPage page, SyncAccumulator accumulator) {
        for (TicketmasterEvent event : page.events()) {
            normalizer.normalize(event).ifPresent(accumulator::add);
        }
    }

    private ConcertSyncResult skipped(String message) {
        log.info("Sincronización de la agenda omitida: {}", message);
        return result(ConcertSyncResult.StatusEnum.SKIPPED, 0, 0, 0, 0,
                null, null, message);
    }

    private ConcertSyncResult result(ConcertSyncResult.StatusEnum status, int fetched, int created,
            int updated, int archived, OffsetDateTime startedAt, OffsetDateTime finishedAt,
            String message) {
        ConcertSyncResult result = new ConcertSyncResult();
        result.setStatus(status);
        result.setFetched(fetched);
        result.setCreated(created);
        result.setUpdated(updated);
        result.setArchived(archived);
        result.setStartedAt(startedAt);
        result.setFinishedAt(finishedAt);
        result.setMessage(message);
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class SyncAccumulator {

        private final Map<String, NormalizedConcert> events = new LinkedHashMap<>();

        private void add(NormalizedConcert event) {
            events.putIfAbsent(event.externalId(), event);
        }

        private List<NormalizedConcert> events() {
            return List.copyOf(events.values());
        }
    }
}
