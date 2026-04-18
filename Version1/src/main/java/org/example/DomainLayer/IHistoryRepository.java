package org.example.DomainLayer;

import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import java.util.List;

public interface IHistoryRepository {
    void add(PurchaseHistory purchaseHistory);

    List<PurchaseHistory> getAll();

    List<PurchaseHistory> getByUserId(int userId);

    List<PurchaseHistory> getByEventId(int eventId);
}
