package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
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

        event.checkAvailabilityOfSittingTickets(ticketIDs);

        //TODO: בדיקת מדיניות רכישה
        //TODO: מהדרישות הבדיקה הזאת קורת רק בהשלמת הרכישה

        //בעיה 1: מה עושים במצב של guest? אי אפשר לתת לו כרטיסים למופעים של 18+
        //בעיה 2: איך נאכוף מכירה של כרטיסים של 18+? ל-repos ו-purchasepolicy אין גישה ל-user repository כדי לשלוף את הגיל של ה-user
        //נצטרך אולי לעשות את זה מ-user repo, כלומר צריך שה-purchasedomainservice יכיל אותה
        //צריך ליצור חוט חיצוני שיעבור וישחרר כרטיסים

        event.reserveSittingTickets(ticketIDs);

        ActivePurchase ap = new ActivePurchase(userID, eventID, ticketIDs, LocalTime.now().plusMinutes(10));
        purchaseRepository.save(ap);
    }

    public void selectStandingTickets(String eventID, int amount, String userID, String areaID)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        event.checkAvailabilityOfStandingTickets(amount, areaID);

        //TODO: בדיקת מדיניות רכישה
        //TODO: מהדרישות הבדיקה הזאת קורת רק בהשלמת הרכישה

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
