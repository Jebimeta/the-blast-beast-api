package com.theblastbeast.integration.ticketmaster;

import com.theblastbeast.properties.TicketmasterProperties;
import com.theblastbeast.util.MadridTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente de Ticketmaster Discovery API.
 *
 * <p>Aplica el límite de 5 peticiones por segundo del plan gratuito, reintenta
 * errores transitorios y traduce la respuesta al modelo interno de la agenda.
 */
@Slf4j
@Component
public class TicketmasterClientImpl implements TicketmasterClient {

    private static final String DEFAULT_BASE_URL = "https://app.ticketmaster.com/discovery/v2";
    private static final String MUSIC_SEGMENT_ID = "KZFzniwnSyZfZ7v7nJ";
    private static final int MAX_PAGE_SIZE = 200;

    private final TicketmasterProperties properties;
    private final RestClient restClient;
    private final Object rateLock = new Object();

    private long lastRequestNanos;

    public TicketmasterClientImpl(TicketmasterProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        String baseUrl = StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().replaceAll("/+$", "")
                : DEFAULT_BASE_URL;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public TicketmasterEventPage search(TicketmasterQuery query) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new TicketmasterException("Falta configurar TICKETMASTER_API_KEY");
        }

        int attempts = Math.max(properties.maxAttempts(), 1);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            rateLimit();
            try {
                TicketmasterEventsResponse response = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/events.json")
                                .queryParam("apikey", properties.apiKey())
                                .queryParam("countryCode", countryCode())
                                .queryParam("segmentId", MUSIC_SEGMENT_ID)
                                .queryParam("locale", locale())
                                .queryParam("sort", "date,asc")
                                .queryParam("size", pageSize(query.size()))
                                .queryParam("page", Math.max(query.page(), 0))
                                .queryParam("startDateTime", MadridTime.ticketmasterStart(query.from()))
                                .queryParam("endDateTime",
                                        MadridTime.ticketmasterEndExclusive(query.to()))
                                .build())
                        .retrieve()
                        .body(TicketmasterEventsResponse.class);

                return toPage(response, Math.max(query.page(), 0));
            } catch (RestClientResponseException exception) {
                lastError = exception;
                if (!retryable(exception.getStatusCode()) || attempt == attempts) {
                    break;
                }
                log.warn("Ticketmaster respondió {}; reintento {}/{}",
                        exception.getStatusCode().value(), attempt, attempts);
                backoff(attempt);
            } catch (ResourceAccessException exception) {
                lastError = exception;
                if (attempt == attempts) {
                    break;
                }
                log.warn("Error de red con Ticketmaster; reintento {}/{}", attempt, attempts);
                backoff(attempt);
            }
        }

        throw new TicketmasterException("No se pudo consultar la API de Ticketmaster", lastError);
    }

    private TicketmasterEventPage toPage(TicketmasterEventsResponse response, int requestedPage) {
        if (response == null) {
            return new TicketmasterEventPage(List.of(), requestedPage, 0, 0);
        }
        List<TicketmasterEvent> events = response.embedded() == null
                || response.embedded().events() == null
                        ? List.of()
                        : response.embedded().events();
        TicketmasterPage page = response.page();
        if (page == null) {
            return new TicketmasterEventPage(events, requestedPage, events.isEmpty() ? 0 : 1,
                    events.size());
        }
        return new TicketmasterEventPage(
                events,
                page.number() == null ? requestedPage : page.number(),
                page.totalPages() == null ? 0 : page.totalPages(),
                page.totalElements() == null ? events.size() : page.totalElements());
    }

    private void rateLimit() {
        Duration interval = properties.requestInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        long intervalNanos = interval.toNanos();
        synchronized (rateLock) {
            long wait = intervalNanos - (System.nanoTime() - lastRequestNanos);
            if (wait > 0) {
                sleepNanos(wait);
            }
            lastRequestNanos = System.nanoTime();
        }
    }

    private void backoff(int attempt) {
        Duration interval = properties.requestInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(interval.multipliedBy(attempt).toNanos());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TicketmasterException("Sincronización interrumpida", exception);
        }
    }

    private void sleepNanos(long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TicketmasterException("Sincronización interrumpida", exception);
        }
    }

    private boolean retryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private int pageSize(int requested) {
        return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
    }

    private String countryCode() {
        return StringUtils.hasText(properties.countryCode()) ? properties.countryCode() : "ES";
    }

    private String locale() {
        return StringUtils.hasText(properties.locale()) ? properties.locale() : "es";
    }
}
