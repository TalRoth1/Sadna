package org.example.DomainLayer.EventAggregate;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class TicketTests {

    @Test
    public void constructor_whenStandingTicketCreated_setsFieldsAndAvailableStatus() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        StandingTicket ticket = new StandingTicket(ticketId, eventId, areaId, 100f);

        assertEquals(ticketId, ticket.getTicketId());
        assertEquals(eventId, ticket.getEventId());
        assertEquals(areaId, ticket.getAreaId());
        assertEquals(100f, ticket.getPrice(), 0.001);
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    public void constructor_whenSittingTicketCreated_setsFieldsSeatDataAndAvailableStatus() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        SittingTicket ticket = new SittingTicket(ticketId, eventId, areaId, 150f, 12, 3);

        assertEquals(ticketId, ticket.getTicketId());
        assertEquals(eventId, ticket.getEventId());
        assertEquals(areaId, ticket.getAreaId());
        assertEquals(150f, ticket.getPrice(), 0.001);
        assertEquals(12, ticket.getSeatNumber());
        assertEquals(3, ticket.getSeatRow());
        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    public void reserve_whenTicketIsAvailable_marksTicketAsReserved() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        ticket.reserve();

        assertEquals(TicketStatus.RESERVED, ticket.getStatus());
    }

    @Test
    public void reserve_whenTicketIsAlreadyReserved_throwsException() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        ticket.reserve();

        assertThrows(IllegalStateException.class, ticket::reserve);
    }

    @Test
    public void releaseReservation_whenTicketIsReserved_marksTicketAsAvailable() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        ticket.reserve();
        ticket.releaseReservation();

        assertEquals(TicketStatus.AVAILABLE, ticket.getStatus());
    }

    @Test
    public void releaseReservation_whenTicketIsAvailable_throwsException() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        assertThrows(IllegalStateException.class, ticket::releaseReservation);
    }

    @Test
    public void markSold_whenTicketIsReserved_marksTicketAsSold() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        ticket.reserve();
        ticket.markSold();

        assertEquals(TicketStatus.SOLD, ticket.getStatus());
    }

    @Test
    public void markSold_whenTicketIsAvailable_throwsException() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        assertThrows(IllegalStateException.class, ticket::markSold);
    }

    @Test
    public void markSold_whenTicketIsAlreadySold_throwsException() {
        StandingTicket ticket = new StandingTicket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100f
        );

        ticket.reserve();
        ticket.markSold();

        assertThrows(IllegalStateException.class, ticket::markSold);
    }
}