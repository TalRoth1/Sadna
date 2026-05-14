package org.example.DomainLayer;

import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(UUID userID);

    ActivePurchase findByID(UUID purchaseID);

    void save(ActivePurchase activePurchase);

    void deleteByID(UUID activePurchaseID);

    List<ActivePurchase> findAll();
}
