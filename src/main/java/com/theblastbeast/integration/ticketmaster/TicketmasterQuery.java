package com.theblastbeast.integration.ticketmaster;

import java.time.LocalDate;

public record TicketmasterQuery(
        LocalDate from,
        LocalDate to,
        int page,
        int size) {
}
