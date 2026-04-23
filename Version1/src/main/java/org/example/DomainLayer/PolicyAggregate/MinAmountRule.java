package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class MinAmountRule implements IPurchaseRule {

    private final int min;

    public MinAmountRule(int min)
    {
        this.min = min;
    }
    public boolean doesHold(ActivePurchase ap, User user, Event event)
    {
        int count = ap.getTicketIDs().size();
        return count >= min;
    }
}
