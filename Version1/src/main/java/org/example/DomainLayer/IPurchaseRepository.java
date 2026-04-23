package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(String userID);
    ActivePurchase findByID(String purchaseID);

    void save(ActivePurchase activePurchase);

    void deleteByID(String activePurchaseID);
}
