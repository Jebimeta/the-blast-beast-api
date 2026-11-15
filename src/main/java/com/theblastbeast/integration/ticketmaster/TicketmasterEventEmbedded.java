package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterEventEmbedded(
        List<TicketmasterVenue> venues,
        List<TicketmasterAttraction> attractions) {
}
