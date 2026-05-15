package org.example.DomainLayer.PolicyManagment;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class AgeRule implements IPurchaseRule {
    private float minAge;

    public AgeRule(float age)
    {
        this.minAge = age;
    }

    public boolean doesHold(ActivePurchase purchase, User user, Event event)
    {
        return user.getAge() >= this.minAge;
    }

    public float getMinAge()
    {
        return this.minAge;
    }
}
