package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.UserAggregate.User;

public interface IPurchaseRule {
    public boolean doesHold(ActivePurchase purchase, User user);
}
