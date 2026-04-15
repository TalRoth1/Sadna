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

    public void SelectSittingTickets(String eventID, List<String> ticketIDs, String userID)
    {
        if (eventID == null || eventID.isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
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

    public void SelectStandingTickets(String eventID, int amount, String areaID, String userID)
    {
        if (eventID == null || eventID.isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
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
