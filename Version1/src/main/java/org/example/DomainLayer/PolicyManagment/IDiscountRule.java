package org.example.DomainLayer.PolicyManagment;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IDiscountRule
{
    public UUID getId();
    public float apply(ActivePurchase purchase);
    public float getDiscountPercent();
}
