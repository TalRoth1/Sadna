package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(UUID userID);

    ActivePurchase findByID(UUID purchaseID);

    void save(ActivePurchase activePurchase);

    void deleteByID(UUID activePurchaseID);
}
