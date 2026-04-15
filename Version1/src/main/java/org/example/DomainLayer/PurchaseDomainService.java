package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;

import java.time.LocalTime;
import java.util.List;

public class PurchaseDomainService
{
    IEventRepository eventRepository;
    IPurchaseRepository purchaseRepository;
    ICompanyRepository companyRepository;

    public void selectSittingTickets(String eventID, List<String> ticketIDs, String userID)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        //TODO: בדיקת מדיניות רכישה

        event.checkAvailabilityOfSittingTickets(ticketIDs);
        event.reserveSittingTickets(ticketIDs);

        ActivePurchase ap = new ActivePurchase(userID, eventID, ticketIDs, LocalTime.now().plusMinutes(10));
        purchaseRepository.save(ap);
    }

    public void selectStandingTickets(String eventID, int amount, String userID, String areaID)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        //TODO: בדיקת מדיניות רכישה


        event.checkAvailabilityOfStandingTickets(amount, areaID);
        List<String> reservedTicketIDs = event.reserveStandingTickets(amount, areaID);

        ActivePurchase ap = new ActivePurchase(userID, eventID, reservedTicketIDs, LocalTime.now().plusMinutes(10));
        purchaseRepository.save(ap);
    }

    private void ensureUserHasNoOtherActivePurchases(String userID)
    {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("לא ניתן להתחיל רכישה חדשה כאשר קיימת רכישה פעילה במערכת");
    }

}
