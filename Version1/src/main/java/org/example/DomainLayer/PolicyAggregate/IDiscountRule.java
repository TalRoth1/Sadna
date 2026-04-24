package org.example.DomainLayer.PolicyAggregate;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IDiscountRule
{
    public boolean apply(ActivePurchase purchase);    
}
