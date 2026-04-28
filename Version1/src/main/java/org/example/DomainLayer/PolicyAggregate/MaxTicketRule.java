package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class MaxTicketRule implements IPurchaseRule {
        private int maxTicket;

    public MaxTicketRule(int amount)
    {
        this.maxTicket = amount;
    }

    public boolean doesHold(ActivePurchase purchase, User user)
    {
        int ticketNum = purchase.getTicketIDs().size();
        return ticketNum <= this.maxTicket;
    }

}
