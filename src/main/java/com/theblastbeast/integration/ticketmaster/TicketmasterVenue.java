package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterVenue(
        String id,
        String name,
        String locale,
        String timezone,
        TicketmasterCity city,
        TicketmasterState state,
        TicketmasterCountry country,
        TicketmasterAddress address,
        TicketmasterLocation location) {
}
