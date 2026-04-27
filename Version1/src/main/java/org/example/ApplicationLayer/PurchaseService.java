package org.example.ApplicationLayer;

import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;
import java.util.UUID;

public class PurchaseService {
    private final PurchaseDomainService purchaseDomainService;

    public PurchaseService(PurchaseDomainService purchaseDomainService) {
        this.purchaseDomainService = purchaseDomainService;
    }

    private void validateAdmin(UUID adminId) {
        // TODO: replace with real admin validation
        throw new IllegalArgumentException("Not yet Implemented");
    }

    public List<PurchaseHistory> getAllHistory(UUID adminId) {
        validateAdmin(adminId);
        return purchaseDomainService.getAllHistory();
    }

    public List<PurchaseHistory> getHistoryByUser(UUID adminId, UUID userId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByUser(userId);
    }

    public List<PurchaseHistory> getHistoryByEvent(UUID adminId, UUID eventId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByEvent(eventId);
    }

    public List<PurchaseHistory> getHistoryByCompany(UUID adminId, UUID companyId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByCompany(companyId);
    }
}
