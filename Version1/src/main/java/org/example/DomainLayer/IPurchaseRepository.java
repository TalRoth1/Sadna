package org.example.DomainLayer;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;

public interface IPurchaseRepository {
    ActivePurchase findByUserID(UUID userID);

    ActivePurchase findByID(UUID purchaseID);

    void save(ActivePurchase activePurchase);

    void deleteByID(UUID activePurchaseID);

    List<ActivePurchase> findAll();

    /**
     * The "at most one active purchase per (user, event)" invariant the spec
     * defines (general doc, page 2: "לרוכש יכולה להיות לכל היותר הזמנה
     * פעילה אחת עבור אירוע יחיד בכל רגע נתון") needs to look up by both
     * fields. A default implementation is provided so existing in-memory
     * fakes don't have to be touched; concrete repos may override for an
     * indexed lookup if it ever matters.
     *
     * @return the user's active purchase for the given event, or {@code null}
     *         if none exists.
     */
    default ActivePurchase findByUserAndEvent(UUID userID, UUID eventID) {
        if (userID == null || eventID == null) {
            return null;
        }
        for (ActivePurchase purchase : findAll()) {
            if (Objects.equals(userID, purchase.getUserID())
                    && Objects.equals(eventID, purchase.getEventID())) {
                return purchase;
            }
        }
        return null;
    }
}
