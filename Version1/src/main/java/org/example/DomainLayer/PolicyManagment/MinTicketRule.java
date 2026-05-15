package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class MinTicketRule implements IPurchaseRule {
    private int minTicket;

    public MinTicketRule(int amount)
    {
        this.minTicket = amount;
    }

    public boolean doesHold(ActivePurchase purchase, User user, Event Event)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum >= this.minTicket;
    }
}
