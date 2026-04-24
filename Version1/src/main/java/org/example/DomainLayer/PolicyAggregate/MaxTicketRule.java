package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public class MaxTicketRule implements IPurchaseRule {
        private int maxTicket;

    public MaxTicketRule(int amount)
    {
        this.maxTicket = amount;
    }

    public boolean doesHold(ActivePurchase purchase)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum <= this.maxTicket;
    }
}
