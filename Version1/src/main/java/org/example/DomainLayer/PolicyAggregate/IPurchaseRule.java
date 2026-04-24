package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRule {
    public boolean doesHold(ActivePurchase purchase);
}
