package com.theblastbeast.integration.ticketmaster;

public interface TicketmasterClient {

    TicketmasterEventPage search(TicketmasterQuery query);
}
