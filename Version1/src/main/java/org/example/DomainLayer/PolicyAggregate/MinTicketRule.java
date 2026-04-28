package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class MinTicketRule implements IPurchaseRule {
    private int minTicket;

    public MinTicketRule(int amount)
    {
        this.minTicket = amount;
    }

    public boolean doesHold(ActivePurchase purchase, User user)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum >= this.minTicket;
    }
}
