package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class MaxAmountRule implements IPurchaseRule
{
    private final int max;

    public MaxAmountRule(int max)
    {
        this.max = max;
    }
    public boolean doesHold(ActivePurchase ap, User user, Event event)
    {
        int count = ap.getTicketIDs().size();
        return count <= max;
    }
}
