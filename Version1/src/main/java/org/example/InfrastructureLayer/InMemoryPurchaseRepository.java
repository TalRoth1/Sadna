package org.example.InfrastructureLayer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

/**
 * Development-only in-memory implementation of {@link IPurchaseRepository}.
 * Mirrors the pattern of the other in-memory repos in this package: a single
 * concurrent map keyed by the aggregate id, plus a second lookup for "find
 * by user" which the purchase flow uses to enforce the one-active-purchase
 * invariant per user.
 */
public class InMemoryPurchaseRepository implements IPurchaseRepository {

    private final Map<UUID, ActivePurchase> purchasesById = new ConcurrentHashMap<>();

    @Override
    public ActivePurchase findByUserID(UUID userID) {
        if (userID == null) {
            return null;
        }
        for (ActivePurchase purchase : purchasesById.values()) {
            if (userID.equals(purchase.getUserID())) {
                return purchase;
            }
        }
        return null;
    }

    @Override
    public ActivePurchase findByID(UUID purchaseID) {
        if (purchaseID == null) {
            return null;
        }
        return purchasesById.get(purchaseID);
    }

    @Override
    public void save(ActivePurchase activePurchase) {
        if (activePurchase == null) {
            throw new IllegalArgumentException("activePurchase is required");
        }
        purchasesById.put(activePurchase.getActivePurchaseId(), activePurchase);
    }

    @Override
    public void deleteByID(UUID activePurchaseID) {
        if (activePurchaseID == null) {
            return;
        }
        purchasesById.remove(activePurchaseID);
    }

    @Override
    public List<ActivePurchase> findAll() {
        return new ArrayList<>(purchasesById.values());
    }

    @Override
    public List<ActivePurchase> findExpiringBefore(LocalDateTime threshold) {
        return purchasesById.values().stream()
                .filter(p -> p.getEndTime().isBefore(threshold))
                .collect(Collectors.toList());
    }
}
