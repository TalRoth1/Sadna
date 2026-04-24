package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class MinTicketRule {
    private int minTicket;

    public MinTicketRule(int amount)
    {
        this.minTicket = amount;
    }

    public boolean doesHold(ActivePurchase purchase)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum >= this.minTicket;
    }
}
