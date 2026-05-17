package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

public class AgeRule implements IPurchaseRule {
    private UUID id = UUID.randomUUID();
    private float minAge;

    public AgeRule(float age)
    {
        this.minAge = age;
    }

    @Override
    public UUID getId()
    {
        return this.id;
    }

    @Override
    public boolean doesHold(ActivePurchase purchase, User user, Event event)
    {
        return user.getAge() >= this.minAge;
    }

    public float getMinAge()
    {
        return this.minAge;
    }
}
