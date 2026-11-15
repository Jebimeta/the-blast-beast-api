package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterDates(
        TicketmasterDate start,
        TicketmasterDate end,
        String timezone,
        TicketmasterStatus status) {
}
