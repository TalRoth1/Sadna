package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class MinTicketRule implements IPurchaseRule {
    private UUID id = UUID.randomUUID();
    private int minTicket;

    public MinTicketRule(int amount)
    {
        this.minTicket = amount;
    }

    @Override
    public UUID getId()
    {
        return this.id;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user, Event Event)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum >= this.minTicket;
    }
}
