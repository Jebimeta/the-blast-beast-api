package com.theblastbeast.integration.ticketmaster;

public class TicketmasterException extends RuntimeException {

    public TicketmasterException(String message) {
        super(message);
    }

    public TicketmasterException(String message, Throwable cause) {
        super(message, cause);
    }
}
