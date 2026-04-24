package org.example.ApplicationLayer;

import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;

public class PurchaseService {
    private final PurchaseDomainService purchaseDomainService;

    public PurchaseService(PurchaseDomainService purchaseDomainService) {
        this.purchaseDomainService = purchaseDomainService;
    }

    private void validateAdmin(int adminId) {
        // TODO: replace with real admin validation
        if (adminId <= 0) {
            throw new IllegalArgumentException("Invalid admin id");
        }
    }

    public List<PurchaseHistory> getAllHistory(int adminId) {
        validateAdmin(adminId);
        return purchaseDomainService.getAllHistory();
    }

    public List<PurchaseHistory> getHistoryByUser(int adminId, int userId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByUser(userId);
    }

    public List<PurchaseHistory> getHistoryByEvent(int adminId, int eventId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByEvent(eventId);
    }

    public List<PurchaseHistory> getHistoryByCompany(int adminId, int companyId) {
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByCompany(companyId);
    }

    public void viewActivePurchase(String activePurchaseId)
    {

    }
    public void changeAmountActivePurchase(String activePurchaseId, int amount)
    {

    }
    public void removeTicketActivePurchase(String activePurchaseId, int ticketID)
    {

    }
}
