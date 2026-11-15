package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterClassification(
        Boolean primary,
        TicketmasterNamed segment,
        TicketmasterNamed genre,
        TicketmasterNamed subGenre,
        TicketmasterNamed type,
        TicketmasterNamed subType) {
}
