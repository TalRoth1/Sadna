package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(String userID);

    void save(ActivePurchase activePurchase);

    ActivePurchase findByID(String purchaseID);
}
