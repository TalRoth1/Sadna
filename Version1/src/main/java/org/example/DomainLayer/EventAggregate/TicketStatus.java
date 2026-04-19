package org.example.DomainLayer.EventAggregate;

/**
 * Ticket inventory state. Purchase flows set RESERVED/SOLD; timeout returns to AVAILABLE.
 */
public enum TicketStatus {
    AVAILABLE,
    RESERVED,
    SOLD
}
