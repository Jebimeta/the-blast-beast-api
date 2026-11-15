package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterEventsResponse(
        @JsonProperty("_embedded") TicketmasterEmbedded embedded,
        TicketmasterPage page) {
}
