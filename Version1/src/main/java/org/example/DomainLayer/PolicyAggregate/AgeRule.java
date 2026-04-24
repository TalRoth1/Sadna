package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public class AgeRule implements IPurchaseRule {
    private double minAge;

    public AgeRule(double age)
    {
        this.minAge = age;
    }

    public boolean doesHold(ActivePurchase purchase)
    {
        String userId = purchase.getUserID();
        User user = IUserRepository.getUser(userId);
        return user.getAge() >= this.minAge;
    }
}
