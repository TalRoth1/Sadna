package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class AgeLimitRule implements IPurchaseRule{

    private final int minAge;

    public AgeLimitRule(int minAge)
    {
        this.minAge = minAge;
    }
    public boolean doesHold(ActivePurchase ap, User user, Event event)
    {
        if (ap.getUserID() == null)
        {
            return ap.getGuestAgeConfirmed();
        }
        else return user.getAge() >= minAge;
    }
}
