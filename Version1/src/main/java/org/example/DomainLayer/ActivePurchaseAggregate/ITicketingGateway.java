package org.example.DomainLayer.ActivePurchaseAggregate;

import java.util.List;

public interface ITicketingGateway
{
    void issueTickets(String userId, int eventId, List<Integer> ticketIds);
}
