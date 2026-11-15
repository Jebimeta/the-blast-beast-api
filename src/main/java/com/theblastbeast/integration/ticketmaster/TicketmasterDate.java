package com.theblastbeast.integration.ticketmaster;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterDate(
        String localDate,
        String localTime,
        String dateTime,
        Boolean dateTBD,
        Boolean dateTBA,
        Boolean timeTBA,
        Boolean noSpecificTime) {
}
