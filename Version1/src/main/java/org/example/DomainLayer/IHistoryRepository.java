package org.example.DomainLayer;

import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import java.util.List;
import java.util.UUID;

public interface IHistoryRepository {
    void add(PurchaseHistory purchaseHistory);

    List<PurchaseHistory> getAll();

    List<PurchaseHistory> getByUserId(UUID userId);

    List<PurchaseHistory> getByEventId(UUID eventId);
}
