package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterPage(
        Integer size,
        Integer totalElements,
        Integer totalPages,
        Integer number) {
}
