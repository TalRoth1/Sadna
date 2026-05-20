package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.dto.SalesReport;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.ActivePurchaseAggregate.IPaymentGateway;
import org.example.DomainLayer.ActivePurchaseAggregate.ITicketingGateway;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PolicyAggregate.DiscountPolicy;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;

public class PurchaseDomainService {
    private final IHistoryRepository historyRepository;
    private final IEventRepository eventRepository;
    private final IPurchaseRepository purchaseRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final ILotteryRepository lotteryRepository;

    IPaymentGateway paymentGateway;
    ITicketingGateway ticketingGateway;


    public PurchaseDomainService(IHistoryRepository historyRepository,
                                 IEventRepository eventRepository,
                                 IPurchaseRepository purchaseRepository,
                                 ICompanyRepository companyRepository,
                                 IUserRepository userRepository,
                                 ILotteryRepository lotteryRepository) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.purchaseRepository = purchaseRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.lotteryRepository = lotteryRepository;
    }

    public void addPurchaseToHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment) {
        if (ticketIds == null || payment == null) {
            throw new IllegalArgumentException("Invalid purchase data");
        }
        PurchaseHistory purchaseHistory = new PurchaseHistory(userId, ticketIds, eventId, payment);
        historyRepository.add(purchaseHistory);
    }

    public ActivePurchase selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean guestAgeConfirmed) {
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

            return activePurchase;
        }
    }


    public ActivePurchase selectStandingTickets(UUID eventID, int amount, UUID userID, UUID areaID, boolean guestAgeConfirmed) {
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

            return ap;
        }
    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null)
            throw new DomainException("Active Purchase Not Found");
        else if (activePurchase.isExpired(LocalDateTime.now()))
            throw new DomainException("The Active Purchase Has Expired");
        else if (!checkLastUpdate(activePurchase))
        {
            cancelActivePurchase(activePurchaseID);
            throw new DomainException("Purchase canceled due to inactivity");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            User user = userRepository.getUser(activePurchase.getUserID())
                    .orElseThrow(() -> new DomainException("User not found"));

            try {
                if (event.getPurchasePolicy() != null) {
                    event.getPurchasePolicy().validate(activePurchase, user, event);
                }
                else
                {
                    Company eventCompany = companyRepository.findByID(event.getCompanyId())
                            .orElseThrow(() -> new DomainException("Company not found for event"));
                    eventCompany.getPurchasePolicy().validate(activePurchase, user, event);
                }
            } catch (DomainException e) {
                event.releaseTickets(activePurchase.getTicketIDs());
                throw e;
            }

            DiscountPolicy relevantDiscountPolicy;
            if (event.getDiscountPolicy() != null)
                relevantDiscountPolicy = event.getDiscountPolicy();
            else
                relevantDiscountPolicy = companyRepository.findByID(event.getCompanyId())
                        .orElseThrow(() -> new DomainException("Company not found for event"))
                        .getDiscountPolicy();

            float finalPrice = relevantDiscountPolicy.applyDiscount(activePurchase);

            boolean paymentSucceeded = paymentGateway.pay(activePurchase.getUserID(), finalPrice, paymentDetails);

            if (!paymentSucceeded)
                throw new DomainException("Payment failed");

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
        return historyRepository.getByUserId(userId);
    }

    public boolean memberExists(UUID userId) {
        return userRepository.getUser(userId).isPresent();
    }

    private void ensureUserHasNoOtherActivePurchases(UUID userID)
    {
        if (purchaseRepository.findByUserID(userID) != null)
            throw new DomainException("Cannot start a new purchase while there is another active purcahse in the system");
    }

    public void updateActivePurchaseSittingTickets(UUID activePurchaseID, List<UUID> newTicketIDs) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseID);
        if (activePurchase == null) {
            throw new DomainException("Active Purchase Not Found");
        }
        else if (!checkLastUpdate(activePurchase))
        {
            cancelActivePurchase(activePurchaseID);
            throw new DomainException("Purchase canceled due to inactivity");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseID);
                throw new DomainException("Active Purchase Expired");
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
                activePurchase.update();
            }
            catch (DomainException | IllegalStateException e) {
                List<UUID> oldticketsId = new ArrayList<>(oldTickets.keySet());
                event.reserveSittingTickets(oldticketsId);
                activePurchase.update();
                throw e;
            }
        }
    }

    public void updateActivePurchaseStandingTickets(UUID activePurchaseId, int newAmount, UUID areaId) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseId);
        if (activePurchase == null) {
            throw new DomainException("Active Purchase Not Found");
        }
        if (!checkLastUpdate(activePurchase))
        {
            cancelActivePurchase(activePurchaseId);
            throw new DomainException("Purchase canceled due to inactivity");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event) {
            if (activePurchase.isExpired(LocalDateTime.now())) {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseId);
                throw new DomainException("Active Purchase Expired");
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
                activePurchase.update();
            }
            catch (DomainException | IllegalStateException e) {
                List<UUID> oldticketsId = new ArrayList<>(oldTickets.keySet());
                event.reserveSittingTickets(oldticketsId);
                activePurchase.update();
                throw e;
            }
        }
    }


    public void cancelActivePurchase(UUID activePurchaseId) {
        ActivePurchase activePurchase = purchaseRepository.findByID(activePurchaseId);
        if (activePurchase == null) {
            throw new DomainException("Active Purchase Not Found");
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
            throw new DomainException("Active Purchase Not Found");
        }
        else if(!checkLastUpdate(activePurchase))
        {
            cancelActivePurchase(activePurchaseId);
            throw new DomainException("Purchase canceled due to inactivity");
        }

        Event event = eventRepository.getById(activePurchase.getEventID());

        synchronized (event)
        {
            if (activePurchase.isExpired(LocalDateTime.now()))
            {
                event.releaseTickets(activePurchase.getTicketIDs());
                purchaseRepository.deleteByID(activePurchaseId);
                throw new DomainException("Active Purchase Expired");
            }
            else
            {
                activePurchase.update();
                return activePurchase;
            }
        }
    }
    public void setPaymentGateway(IPaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }
    public void setTicketingGateway(ITicketingGateway ticketingGateway) {
        this.ticketingGateway = ticketingGateway;
    }

    public boolean validateAdmin(UUID adminId) {
        if (!userRepository.existsAdmin(adminId)) {
            return false;
        }
        return true;
    }

    public boolean isMemberLoggedIn(UUID memberId) {
        User user = userRepository.getUser(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getStatus() == UserStatus.LOGGED_IN;
    }

    public boolean isMember(UUID memberId) {
        User user = userRepository.getUser(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getRole() == UserRole.MEMBER;
    }


    public boolean eventExists(UUID eventId) {
        return eventRepository.getById(eventId) != null;
    }

    public boolean isCompanyOwnerOfEvent(String ownerName, UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            return false;
        }

        Company company = companyRepository.findByID(event.getCompanyId())
                .orElse(null);

        if (company == null) {
            return false;
        }

        return company.isOwner(ownerName);
    }

    public boolean checkLastUpdate(ActivePurchase activePurchase)
    {
        return ChronoUnit.MINUTES.between(LocalDateTime.now(), activePurchase.getLastUpdate()) <= activePurchase.getMaxWaitTime();
    }

    public void registerToLottery(UUID eventId, UUID memberId, int ticketAmount) {
        if (eventId == null || memberId == null) {
            throw new DomainException("Event ID and member ID are required");
        }

        if (ticketAmount <= 0) {
            throw new DomainException("Ticket amount must be greater than zero");
        }

        if (!memberExists(memberId)) {
            throw new DomainException("Member does not exist");
        }

        if (!isMember(memberId)) {
            throw new DomainException("User is not a member");
        }

        if (!isMemberLoggedIn(memberId)) {
            throw new DomainException("Member is not logged in");
        }

        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event does not exist");
        }

        PuchaseLottery lottery = lotteryRepository.findByEventID(eventId);
        if (lottery == null) {
            throw new DomainException("Lottery does not exist for this event");
        }

        lottery.registerMember(memberId.toString(), ticketAmount, LocalDateTime.now());

        lotteryRepository.save(lottery);
    }

    public void drawLotteryForEvent(UUID eventId, LocalDateTime codeExpiry) {
        if (eventId == null) {
            throw new DomainException("Event ID is required");
        }

        if (codeExpiry == null) {
            throw new DomainException("Code expiry is required");
        }

        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event does not exist");
        }

        PuchaseLottery lottery = lotteryRepository.findByEventID(eventId);
        if (lottery == null) {
            throw new DomainException("Lottery does not exist for this event");
        }

        int availableTickets = event.getTicketsView().size();

        lottery.drawWinners(availableTickets, codeExpiry);

        lotteryRepository.save(lottery);
    }

    public SalesReport getSalesReportForOwner(String ownerUsername, UUID companyId) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        List<UUID> eventsUnderOwner = company.getEventsUnderOwner(ownerUsername);
        List<PurchaseHistory> relevantPurchases = new ArrayList<>();
        for (UUID eventId : eventsUnderOwner) {
            relevantPurchases.addAll(historyRepository.getByEventId(eventId));
        }

        // Calculate total revenue
        double totalRevenue = relevantPurchases.stream()
                .mapToDouble(purchase -> purchase.getPayment().getTotal())
                .sum();

        List<UUID> soldTicketIds = relevantPurchases.stream()
                .flatMap(purchase -> purchase.getTicketIds().stream())
                .collect(Collectors.toList());

        return new SalesReport(eventsUnderOwner, soldTicketIds, totalRevenue);
    }
}