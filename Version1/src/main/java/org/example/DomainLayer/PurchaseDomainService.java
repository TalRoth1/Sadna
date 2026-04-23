package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.UserAggregate.User;

import java.time.LocalDateTime;
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

        synchronized (event)
        {
            event.reserveSittingTickets(ticketIDs);
            ActivePurchase activePurchase = new ActivePurchase(userID, eventID, ticketIDs, LocalDateTime.now().plusMinutes(10));
            activePurchase.SetGuestAgeConfirmed(guestAgeConfirmed);

            User user = userRepository.findByID(userID);

            try
            {
                //אם לאירוע יש מדיניות
                if (event.getPurchasePolicy() != null) {
                    event.getPurchasePolicy().validate(activePurchase, user, event);
                }
                else
                {
                    Company eventCompany = companyRepository.findByID(event.getCompanyId());
                    eventCompany.getPurchasePolicy().validate(activePurchase, user, event);
                }
            }
            catch (DomainException e)
            {
                event.releaseTickets(ticketIDs);
                throw e;
            }
            //נצטרך אולי לעשות את זה מ-user repo, כלומר צריך שה-purchasedomainservice יכיל אותה
            //צריך ליצור חוט חיצוני שיעבור וישחרר כרטיסים

            purchaseRepository.save(activePurchase);
        }

    }

    public void selectStandingTickets(int eventID, int amount, String userID, int areaID, boolean guestAgeConfirmed)
    {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.findByID(eventID);

        synchronized (event)
        {
            event.checkAvailabilityOfStandingTickets(amount, areaID);

            List<Integer> reservedTicketIDs = event.reserveStandingTickets(amount, areaID);

            ActivePurchase ap = new ActivePurchase(userID, eventID, reservedTicketIDs, LocalDateTime.now().plusMinutes(10));
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


    }

    private void ensureUserHasNoOtherActivePurchases(String userID)
    {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("לא ניתן להתחיל רכישה חדשה כאשר קיימת רכישה פעילה במערכת");
    }

}
