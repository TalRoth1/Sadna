package org.example.ApplicationLayer;

import java.util.Set;
import java.util.UUID;

public interface ITicketingGateway
{
    String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds);
}
