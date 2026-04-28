package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(UUID userID);

    void save(ActivePurchase activePurchase);
}
