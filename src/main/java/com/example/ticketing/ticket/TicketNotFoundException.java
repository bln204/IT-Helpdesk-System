package com.example.ticketing.ticket;

/**
 * Exception thrown when a ticket is not found.
 */
public class TicketNotFoundException extends RuntimeException {
    
    public TicketNotFoundException(Long id) {
        super("Ticket not found: " + id);
    }
    
    public TicketNotFoundException(String ticketNumber) {
        super("Ticket not found: " + ticketNumber);
    }
}
