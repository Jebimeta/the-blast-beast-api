package com.theblastbeast.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blastbeast.ticketmaster")
public record TicketmasterProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String countryCode,
        String locale,
        String syncCron,
        int syncDays,
        int windowDays,
        int pageSize,
        int maxPagesPerWindow,
        int maxAttempts,
        Duration requestInterval) {}
