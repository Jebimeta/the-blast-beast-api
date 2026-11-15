package com.theblastbeast.integration.ticketmaster;

import java.util.List;

public record TicketmasterEventPage(
        List<TicketmasterEvent> events,
        int page,
        int totalPages,
        long totalElements) {

    public TicketmasterEventPage {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
