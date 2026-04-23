package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.ActivePurchaseAggregate.IPaymentGateway;
import org.example.DomainLayer.ActivePurchaseAggregate.ITicketingGateway;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.UserAggregate.User;

import java.time.LocalDateTime;
import java.util.List;

public class PurchaseDomainService
{
    IEventRepository eventRepository;
    IPurchaseRepository purchaseRepository;
    ICompanyRepository companyRepository;
    IUserRepository userRepository;

    IPaymentGateway paymentGateway;
    ITicketingGateway ticketingGateway;

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

    public void completePurchase(String activePurchaseID)
    {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null)
            throw new DomainException("לא נמצאה הזמנה פעילה להשלמת רכישה");
        else if (activePurchase.isExpired(LocalDateTime.now()))
            throw new DomainException("ההזמנה שרצינו להשלים פגת תוקף");

        Event event = eventRepository.findByID(activePurchase.getEventID());

        synchronized (event)
        {
            DiscountPolicy relevantDiscountPolicy;
            if (event.getDiscountPolicy() != null)
                relevantDiscountPolicy = event.getDiscountPolicy();
            else relevantDiscountPolicy = companyRepository.findByID(event.getCompanyId()).getDiscountPolicy();

            relevantDiscountPolicy.apply(activePurchase, event);

            double finalPrice = activePurchase.calculateCurrentTotalPrice();

            boolean paymentSucceeded = paymentGateway.pay(activePurchase.getUserID(), finalPrice);

            if (!paymentSucceeded)
                throw new DomainException("התשלום נכשל");

            ticketingGateway.issueTickets(activePurchase.getUserID(), activePurchase.getEventID(), activePurchase.getTicketIDs());

            event.sellTickets(activePurchase.getTicketIDs());
            purchaseRepository.deleteByID(activePurchaseID);

        }

    }

    private void ensureUserHasNoOtherActivePurchases(String userID)
    {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("לא ניתן להתחיל רכישה חדשה כאשר קיימת רכישה פעילה במערכת");
    }

}
