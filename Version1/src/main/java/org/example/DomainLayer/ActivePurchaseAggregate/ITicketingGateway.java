package org.example.DomainLayer.ActivePurchaseAggregate;

import java.util.Set;
import java.util.UUID;

public interface ITicketingGateway
{
    void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds);
}
