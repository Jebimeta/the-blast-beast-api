package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterImage(
        String ratio,
        String url,
        Integer width,
        Integer height,
        Boolean fallback) {
}
