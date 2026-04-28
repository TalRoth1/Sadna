package org.example.DomainLayer.Repositories;

import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HistoryRepository implements IHistoryRepository{
    private final List<PurchaseHistory> historyList = new ArrayList<>();

    public void add(PurchaseHistory purchaseHistory) {
        historyList.add(purchaseHistory);
    }

    public List<PurchaseHistory> getAll() {
        return new ArrayList<>(historyList);
    }

    public List<PurchaseHistory> getByUserId(UUID userId) {
        return historyList.stream()
                .filter(h -> h.getUserId() == userId)
                .collect(Collectors.toList());
    }

    public List<PurchaseHistory> getByEventId(UUID eventId) {
        return historyList.stream()
                .filter(h -> h.getEventId() == eventId)
                .collect(Collectors.toList());
    }
}
