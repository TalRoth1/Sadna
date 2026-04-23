package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

import java.time.LocalTime;
import java.util.List;

public class PurchaseDomainService
{
    IEventRepository eventRepository;
    IPurchaseRepository purchaseRepository;
    ICompanyRepository companyRepository;
    IUserRepository userRepository;

    public void selectSittingTickets(int eventID, List<Integer> ticketIDs, String userID, boolean guestAgeConfirmed)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        ActivePurchase ap = new ActivePurchase(userID, eventID, ticketIDs, LocalTime.now().plusMinutes(10));
        ap.SetGuestAgeConfirmed(guestAgeConfirmed);

        User user = userRepository.findByID(userID);

        try
        {
            //אם לאירוע יש מדיניות
            if (event.getPurchasePolicy() != null) {
                event.getPurchasePolicy().validate(ap, user, event);
            }
            else
            {
                Company eventCompany = companyRepository.findByID(event.getCompanyId());
                eventCompany.getPurchasePolicy().validate(ap, user, event);
            }
        }
        catch (DomainException e)
        {
            event.releaseTickets(ticketIDs);
            throw e;
        }



        //TODO: בדיקת מדיניות רכישה
        //TODO: מהדרישות הבדיקה הזאת קורת רק בהשלמת הרכישה

        //בעיה 1: מה עושים במצב של guest? אי אפשר לתת לו כרטיסים למופעים של 18+
        //בעיה 2: איך נאכוף מכירה של כרטיסים של 18+? ל-repos ו-purchasepolicy אין גישה ל-user repository כדי לשלוף את הגיל של ה-user
        //נצטרך אולי לעשות את זה מ-user repo, כלומר צריך שה-purchasedomainservice יכיל אותה
        //צריך ליצור חוט חיצוני שיעבור וישחרר כרטיסים

        event.reserveSittingTickets(ticketIDs);

        purchaseRepository.save(ap);
    }

    public void selectStandingTickets(int eventID, int amount, String userID, int areaID, boolean guestAgeConfirmed)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        event.checkAvailabilityOfStandingTickets(amount, areaID);

        List<Integer> reservedTicketIDs = event.reserveStandingTickets(amount, areaID);

        ActivePurchase ap = new ActivePurchase(userID, eventID, reservedTicketIDs, LocalTime.now().plusMinutes(10));
        ap.SetGuestAgeConfirmed(guestAgeConfirmed);

        User user = userRepository.findByID(userID);

        try //אם כל הולדיציה, יעני הבדיקה של הבחירה של ה-user נכשלה, נחזיר את הכרטיסים
        {
            if (event.getPurchasePolicy() != null) {
                event.getPurchasePolicy().validate(ap, user, event);
            }
            else
            {
                Company eventCompany = companyRepository.findByID(event.getCompanyId());
                eventCompany.getPurchasePolicy().validate(ap, user, event);
            }
        }
        catch (DomainException e)
        {
            event.releaseTickets(reservedTicketIDs);
            throw e;
        }


        purchaseRepository.save(ap);
    }

    private void ensureUserHasNoOtherActivePurchases(String userID)
    {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("לא ניתן להתחיל רכישה חדשה כאשר קיימת רכישה פעילה במערכת");
    }

}
