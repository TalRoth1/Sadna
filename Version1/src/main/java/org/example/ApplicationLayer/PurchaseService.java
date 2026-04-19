package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;

import java.util.List;

public class PurchaseService
{
    PurchaseDomainService purchaseDomainService;

    public PurchaseService(PurchaseDomainService purchaseDomainService)
    {
        this.purchaseDomainService = purchaseDomainService;
    }

    public void SelectSittingTickets(int eventID, List<Integer> ticketIDs, String userID)
    {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null || userID.isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        try {
            purchaseDomainService.selectSittingTickets(eventID, ticketIDs, userID);
        } catch (DomainException e) {
            handleDomainError(e);
        }
    }

    private void handleDomainError(DomainException e) {
    }

    public void SelectStandingTickets(int eventID, int amount, int areaID, String userID)
    {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null || userID.isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        try {
            purchaseDomainService.selectStandingTickets(eventID, amount, userID, areaID);
        } catch (DomainException e) {
            handleDomainError(e);
        }
    }
}
