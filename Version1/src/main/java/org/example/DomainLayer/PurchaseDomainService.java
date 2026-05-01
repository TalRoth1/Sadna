package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.DomainLayer.ActivePurchaseAggregate.IPaymentGateway;
import org.example.DomainLayer.ActivePurchaseAggregate.ITicketingGateway;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PurchaseDomainService {
    private final IHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final IPurchaseRepository purchaseRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    IPaymentGateway paymentGateway;
    ITicketingGateway ticketingGateway;


    public PurchaseDomainService(IHistoryRepository historyRepository,
                                 IEventRepository eventRepository,
                                 IPurchaseRepository purchaseRepository,
                                 ICompanyRepository companyRepository,
                                 IUserRepository userRepository) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.purchaseRepository = purchaseRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public void addPurchaseToHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment) {
        if (ticketIds == null || payment == null) {
            throw new IllegalArgumentException("Invalid purchase data");
        }
        PurchaseHistory purchaseHistory = new PurchaseHistory(userId, ticketIds, eventId, payment);
        historyRepository.add(purchaseHistory);
    }

    public void selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean guestAgeConfirmed) {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.getById(eventID);

        synchronized (event) {
            event.reserveSittingTickets(ticketIDs);
            LinkedHashMap<UUID, Float> ticketBasePrices = new LinkedHashMap<>();

            for (UUID ticketId : ticketIDs) {
                ticketBasePrices.put(ticketId, event.getTicket(ticketId).getPrice());
            }

            ActivePurchase activePurchase = new ActivePurchase(userID, eventID, ticketBasePrices, LocalDateTime.now().plusMinutes(10));
            activePurchase.SetGuestAgeConfirmed(guestAgeConfirmed);

            purchaseRepository.save(activePurchase);
        }

    }


    public void selectStandingTickets(UUID eventID, int amount, UUID userID, UUID areaID, boolean guestAgeConfirmed) {
        ensureUserHasNoOtherActivePurchases(userID);
        Event event = eventRepository.getById(eventID);

        synchronized (event) {

            List<UUID> reservedTicketIDs = event.reserveStandingTickets(amount, areaID);

            LinkedHashMap<UUID, Float> ticketBasePrices = new LinkedHashMap<>();

            for (UUID ticketId : reservedTicketIDs) {
                ticketBasePrices.put(ticketId, event.getTicket(ticketId).getPrice());
            }

            ActivePurchase ap = new ActivePurchase(userID, eventID, ticketBasePrices, LocalDateTime.now().plusMinutes(10));
            ap.SetGuestAgeConfirmed(guestAgeConfirmed);

            purchaseRepository.save(ap);
        }


    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null)
            throw new DomainException("לא נמצאה הזמנה פעילה להשלמת רכישה");
        else if (activePurchase.isExpired(LocalDateTime.now()))
            throw new DomainException("ההזמנה שרצינו להשלים פגת תוקף");

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            User user = userRepository.getUser(activePurchase.getUserID()).get();

            try {
                if (event.getPurchasePolicy() != null) {
                    event.getPurchasePolicy().validate(activePurchase, user);
                } else {
                    Company eventCompany = companyRepository.findByID(event.getCompanyId()).get();
                    eventCompany.getPurchasePolicy().validate(activePurchase, user);
                }
            } catch (DomainException e) {
                event.releaseTickets(activePurchase.getTicketIDs());
                throw e;
            }

            DiscountPolicy relevantDiscountPolicy;
            if (event.getDiscountPolicy() != null)
                relevantDiscountPolicy = event.getDiscountPolicy();
            else relevantDiscountPolicy = companyRepository.findByID(event.getCompanyId()).get().getDiscountPolicy();

            float finalPrice = relevantDiscountPolicy.applyDiscount(activePurchase);

            boolean paymentSucceeded = paymentGateway.pay(activePurchase.getUserID(), finalPrice, paymentDetails);

            if (!paymentSucceeded)
                throw new DomainException("התשלום נכשל");

            try {
                ticketingGateway.issueTickets(activePurchase.getUserID(), activePurchase.getEventID(), activePurchase.getTicketIDs().keySet());
            } catch (DomainException e) {
                event.releaseTickets(activePurchase.getTicketIDs());
                throw e;
            }


            event.sellTickets(activePurchase.getTicketIDs().keySet());
            purchaseRepository.deleteByID(activePurchaseID);

        }

    }

    public List<PurchaseHistory> getAllHistory() {
        return historyRepository.getAll();
    }

    public List<PurchaseHistory> getHistoryByUser(UUID userId) {
        return historyRepository.getByUserId(userId);
    }

    public List<PurchaseHistory> getHistoryByEvent(UUID eventId) {
        return historyRepository.getByEventId(eventId);
    }

    public List<PurchaseHistory> getHistoryByCompany(UUID companyId) {
        List<PurchaseHistory> result = new ArrayList<>();

        for (PurchaseHistory history : historyRepository.getAll()) {
            Event event = eventRepository.getById(history.getEventId());

            if (event != null && event.getCompanyId() == companyId) {
                result.add(history);
            }
        }

        return result;
    }

    public List<PurchaseHistory> getPurchaseHistoryForMember(UUID userId) {

        User user = userRepository.getUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!(user.getRole() == UserRole.MEMBER)) {
            throw new IllegalArgumentException("User is not a member");
        }


        if (!(user.getStatus() == UserStatus.LOGGED_IN)) {
            throw new IllegalArgumentException("Member is not logged in");
        }

        return historyRepository.getByUserId(userId);
    }

    private void ensureUserHasNoOtherActivePurchases(UUID userID) {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("Cannot start a new purchase while there is another active purcahse in the system");
    }

    public void updateActivePurchaseSittingTickets(UUID activePurchaseID, List<UUID> newTicketIDs) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                throw new DomainException("פג תוקף ההזמנה הפעילה");
            }

            Map<UUID, Float> oldTickets = activePurchase.getTicketIDs();

            try {
                event.releaseTickets(oldTickets);
                event.reserveSittingTickets(newTicketIDs);

                LinkedHashMap<UUID, Float> newTicketPrices = new LinkedHashMap<>();
                for (UUID ticketId : newTicketIDs) {
                    newTicketPrices.put(ticketId, event.getTicket(ticketId).getPrice());
                }

                activePurchase.replaceTickets(newTicketPrices);
                purchaseRepository.save(activePurchase);
            } catch (DomainException | IllegalStateException e) {
                List<UUID> oldticketsId = new ArrayList<>(oldTickets.keySet());
                event.reserveSittingTickets(oldticketsId);
                throw e;
            }
        }
    }

    public void updateActivePurchaseStandingTickets(UUID activePurchaseId, int newAmount, UUID areaId) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseId);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                throw new DomainException("פג תוקף ההזמנה הפעילה");
            }

            Map<UUID, Float> oldTickets = activePurchase.getTicketIDs();

            try {
                event.releaseTickets(oldTickets);
                List<UUID> newTicketIDs = event.reserveStandingTickets(newAmount, areaId);

                LinkedHashMap<UUID, Float> newTicketPrices = new LinkedHashMap<>();
                for (UUID ticketId : newTicketIDs) {
                    newTicketPrices.put(ticketId, event.getTicket(ticketId).getPrice());
                }

                activePurchase.replaceTickets(newTicketPrices);
                purchaseRepository.save(activePurchase);
            } catch (DomainException | IllegalStateException e) {
                List<UUID> oldticketsId = new ArrayList<>(oldTickets.keySet());
                event.reserveSittingTickets(oldticketsId);
                throw e;
            }
        }
    }


    public void cancelActivePurchase(UUID activePurchaseId) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseId);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            event.releaseTickets(activePurchase.getTicketIDs());
            purchaseRepository.deleteByID(activePurchaseId);
        }
    }

    public ActivePurchase viewActivePurchase(UUID activePurchaseId) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseId);
        if (activePurchase == null) {
            throw new DomainException("לא נמצאה הזמנה פעילה");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        if (activePurchase.isExpired(LocalDateTime.now())) {
            throw new DomainException("פג תוקף ההזמנה שברצוננו לצפות");
        }
        return activePurchase;
    }
}
