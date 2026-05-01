package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class AgeRule implements IPurchaseRule {
    private float minAge;

    public AgeRule(float age)
    {
        this.minAge = age;
    }

    public boolean doesHold(ActivePurchase purchase, User user)
    {
        return user.getAge() >= this.minAge;
    }
}
