package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class MaxTicketRule implements IPurchaseRule {
    private UUID id = UUID.randomUUID();
    private int maxTicket;

    @Override
    public UUID getId()
    {
        return this.id;
    }

    public MaxTicketRule(int amount)
    {
        this.maxTicket = amount;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user, Event event)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum <= this.maxTicket;
    }

    public int getMaxTicket() {
        return this.maxTicket;
    }
}
