package org.example.ApplicationLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
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

    //Active purchase management methods
    public ActivePurchase viewActivePurchase(String activePurchaseId)
    {
        if (activePurchaseId == null || activePurchaseId.isEmpty()) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            return purchaseDomainService.viewActivePurchase(activePurchaseId);
        }
        catch (DomainException e)
        {
            handleDomainError(e);
            return null;
        }
    }
    public void cancelActivePurchase(String activePurchaseId)
    {
        if (activePurchaseId == null || activePurchaseId.isEmpty()) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            purchaseDomainService.cancelActivePurchase(activePurchaseId);
        }
        catch (DomainException e)
        {
            handleDomainError(e);
        }
    }
    public void updateActivePurchaseSittingTickets(String activePurchaseId, List<Integer> newTicketIds)
    {
        if (activePurchaseId == null || activePurchaseId.isEmpty()) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newTicketIds == null || newTicketIds.isEmpty()) {
            throw new IllegalArgumentException("New ticket IDs are required");
        }
        try
        {
            purchaseDomainService.updateActivePurchaseSittingTickets(activePurchaseId, newTicketIds);
        }
        catch (DomainException e)
        {
            handleDomainError(e);
        }
    }

    public void updateActivePurchaseStandingTickets(String activePurchaseId, int newAmount, int areaId) {
        if (activePurchaseId == null || activePurchaseId.isEmpty()) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newAmount <= 0) {
            throw new IllegalArgumentException("New amount must be non-negative");
        }
        try {
            purchaseDomainService.updateActivePurchaseStandingTickets(activePurchaseId, newAmount, areaId);
        } catch (DomainException e) {
            handleDomainError(e);
        }
    }

}
