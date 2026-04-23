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

    public void selectSittingTickets(int eventID, List<Integer> ticketIDs, String userID, boolean isConfirmedAge)
    {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null || userID.isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        try {
            purchaseDomainService.selectSittingTickets(eventID, ticketIDs, userID, isConfirmedAge);
        } catch (DomainException e) {
            handleDomainError(e);
        }
    }
    public void completePurchase(String activePurchaseID)
    {
        if (activePurchaseID == null || activePurchaseID.isEmpty()) {
            throw new IllegalArgumentException("Active Purchase ID is required");
        }
        try
        {
            purchaseDomainService.completePurchase(activePurchaseID);
        }
        catch (DomainException e) {
            handleDomainError(e);
        }
    }


    //TODO: מתודת reserveTickets כדי לעמוד בדרישה שכל יוז קייס ייוצג ב-application service

    private void handleDomainError(DomainException e) {
    }

    public void selectStandingTickets(int eventID, int amount, int areaID, String userID, boolean isConfirmedAge)
    {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null || userID.isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        try {
            purchaseDomainService.selectStandingTickets(eventID, amount, userID, areaID, isConfirmedAge);
        } catch (DomainException e) {
            handleDomainError(e);
        }
    }
}
