package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.dto.SalesReport;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.stereotype.Service;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;

@Service
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
        this(historyRepository, eventRepository, purchaseRepository,
                companyRepository, userRepository, lotteryRepository,
                null, null);
    }

    /**
     * Constructor with the external gateways pre-wired. {@code BeanConfig}
     * uses this one so Spring guarantees both gateways are non-null before
     * any request can hit {@link #completePurchase}. Tests keep using the
     * 6-arg constructor above and inject custom lambdas via the setters.
     */
    public PurchaseDomainService(IHistoryRepository historyRepository,
                                 IEventRepository eventRepository,
                                 IPurchaseRepository purchaseRepository,
                                 ICompanyRepository companyRepository,
                                 IUserRepository userRepository,
                                 ILotteryRepository lotteryRepository,
                                 IPaymentGateway paymentGateway,
                                 ITicketingGateway ticketingGateway) {
        this.historyRepository = historyRepository;
        this.eventRepository = eventRepository;
        this.purchaseRepository = purchaseRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.lotteryRepository = lotteryRepository;
        this.paymentGateway = paymentGateway;
        this.ticketingGateway = ticketingGateway;
    }

    public void addPurchaseToHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment) {
        if (ticketIds == null || payment == null) {
            throw new IllegalArgumentException("Invalid purchase data");
        }
        PurchaseHistory purchaseHistory = new PurchaseHistory(userId, ticketIds, eventId, payment);
        historyRepository.add(purchaseHistory);
    }

    /**
     * Null-safe read of an Event by id, intended for DTO denormalization in the
     * Application layer (e.g. populating eventName/eventDate/eventLocation on
     * PurchaseHistoryDTO and ActivePurchaseDTO). Returns {@code null} if the
     * id is missing or no matching event exists, so callers can fall back to
     * defaults without try/catch.
     */
    public Event findEventById(UUID eventId) {
        if (eventId == null) {
            return null;
        }
        return eventRepository.getById(eventId);
    }

    public ActivePurchase selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean guestAgeConfirmed) {
        return selectSittingTickets(eventID, ticketIDs, userID, guestAgeConfirmed, null);
    }

    public ActivePurchase selectSittingTicketsWithLotteryCode(UUID eventID, List<UUID> ticketIDs, UUID userID,
                                                              boolean guestAgeConfirmed, String accessCode) {
        return selectSittingTickets(eventID, ticketIDs, userID, guestAgeConfirmed, accessCode);
    }

    public ActivePurchase selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID,
                                               boolean guestAgeConfirmed, String accessCode) {
        ensureUserHasNoOtherActivePurchaseForEvent(userID, eventID);

        validateSelectionEligibility(eventID, userID, accessCode);

        Event event = eventRepository.getById(eventID);
        if (event == null) {
            throw new DomainException("Event does not exist");
        }

        synchronized (event) {
            event.reserveSittingTickets(ticketIDs);

            LinkedHashMap<UUID, Float> ticketBasePrices = new LinkedHashMap<>();

            for (UUID ticketId : ticketIDs) {
                ticketBasePrices.put(ticketId, event.getTicket(ticketId).getPrice());
            }

            ActivePurchase activePurchase =
                    new ActivePurchase(userID, eventID, ticketBasePrices, LocalDateTime.now().plusMinutes(10));

            activePurchase.SetGuestAgeConfirmed(guestAgeConfirmed);

            purchaseRepository.save(activePurchase);
            return activePurchase;
        }
    }

    public ActivePurchase selectStandingTickets(UUID eventID, int amount, UUID userID, UUID areaID,
                                                boolean guestAgeConfirmed) {
        return selectStandingTickets(eventID, amount, userID, areaID, guestAgeConfirmed, null);
    }

    public ActivePurchase selectStandingTicketsWithLotteryCode(UUID eventID, int amount, UUID userID, UUID areaID,
                                                               boolean guestAgeConfirmed, String accessCode) {
        return selectStandingTickets(eventID, amount, userID, areaID, guestAgeConfirmed, accessCode);
    }

    public ActivePurchase selectStandingTickets(UUID eventID, int amount, UUID userID, UUID areaID,
                                                boolean guestAgeConfirmed, String accessCode) {
        ensureUserHasNoOtherActivePurchaseForEvent(userID, eventID);

        validateSelectionEligibility(eventID, userID, accessCode);

        Event event = eventRepository.getById(eventID);
        if (event == null) {
            throw new DomainException("Event does not exist");
        }

        synchronized (event) {
            List<UUID> reservedTicketIDs = event.reserveStandingTickets(amount, areaID);

            LinkedHashMap<UUID, Float> ticketBasePrices = new LinkedHashMap<>();

            for (UUID ticketId : reservedTicketIDs) {
                ticketBasePrices.put(ticketId, event.getTicket(ticketId).getPrice());
            }

            ActivePurchase activePurchase =
                    new ActivePurchase(userID, eventID, ticketBasePrices, LocalDateTime.now().plusMinutes(10));

            activePurchase.SetGuestAgeConfirmed(guestAgeConfirmed);

            purchaseRepository.save(activePurchase);
            return activePurchase;
        }
    }


    public boolean completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode) 
    { //returns true if last ticket to event was bought
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
            // Use orElseThrow so that a stale userID (e.g. cached in a
            // browser session after the in-memory user repository was
            // wiped on a server restart) surfaces as a clean
            // DomainException -> 400 instead of an opaque
            // NoSuchElementException -> 500.
            User user = userRepository.getUser(activePurchase.getUserID())
                    .orElseThrow(() -> new DomainException(
                            "Your session is no longer valid. Please refresh the page and try again."));

            try {
                if (event.getPurchasePolicy() != null) {
                    event.getPurchasePolicy().validate(activePurchase, user, event);
                }
                else
                {
                    Company eventCompany = companyRepository.findByID(event.getCompanyId()).get();
                    eventCompany.getPurchasePolicy().validate(activePurchase, user, event);
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

            // Step 1: charge the user. A "declined" outcome is a normal
            // business answer (not an exception), so we leave the
            // reservation in place and let the user retry with a
            // different card. ActivePurchaseCleaner will eventually
            // sweep it if they give up.
            boolean paymentSucceeded =
                    paymentGateway.pay(activePurchase.getUserID(), finalPrice, paymentDetails);
            if (!paymentSucceeded) {
                throw new DomainException(
                        "Payment was declined. Please try a different payment method.");
            }

            // Step 2: ask the ticketing system to issue the tickets. If
            // this throws AFTER we successfully charged the user, we owe
            // them a refund — this is the cancellation + refund path the
            // assignment asks us to exercise.
            try {
                ticketingGateway.issueTickets(
                        activePurchase.getUserID(),
                        activePurchase.getEventID(),
                        activePurchase.getTicketIDs().keySet());
            } catch (RuntimeException ticketingFailure) {
                compensateFailedTicketing(activePurchase, finalPrice, paymentDetails, ticketingFailure);
                // unreachable — compensateFailedTicketing always throws. We
                // throw here too so the compiler is satisfied without anyone
                // mistakenly reading this as a "silent failure" return.
                throw new IllegalStateException(
                        "compensateFailedTicketing should always throw");
            }

            event.sellTickets(activePurchase.getTicketIDs().keySet());
            purchaseRepository.deleteByID(activePurchaseID);

            Payment payment = new Payment(finalPrice, "Valid payment");
            addPurchaseToHistory(
                    activePurchase.getUserID(),
                    new ArrayList<>(activePurchase.getTicketIDs().keySet()),
                    activePurchase.getEventID(),
                    payment
            );

            for (Map.Entry<UUID, Ticket> ticket : event.getTicketsView().entrySet())
                {
                    if(ticket.getValue().getStatus() != TicketStatus.SOLD)
                        return false;
                }
            return true;
        }

    }

    /**
     * Compensating transaction invoked when ticketing fails after a
     * successful charge: refund the payment, release the held tickets,
     * delete the active purchase and surface a user-friendly message.
     *
     * If the refund itself fails we log loudly and surface a different
     * message — at that point operator intervention is required, so we
     * don't want to swallow it under "tickets could not be issued".
     */
    private void compensateFailedTicketing(ActivePurchase activePurchase,
                                           float chargedAmount,
                                           PaymentDetails paymentDetails,
                                           RuntimeException originalFailure) {
        boolean refunded;
        try {
            refunded = paymentGateway.refund(
                    activePurchase.getUserID(), chargedAmount, paymentDetails);
        } catch (RuntimeException refundError) {
            refunded = false;
        }

        Event event = eventRepository.getById(activePurchase.getEventID());
        if (event != null) {
            event.releaseTickets(activePurchase.getTicketIDs());
        }
        purchaseRepository.deleteByID(activePurchase.getActivePurchaseId());

        if (refunded) {
            throw new DomainException(
                    "Tickets could not be issued (" + originalFailure.getMessage()
                            + "). Your payment of " + chargedAmount + " has been refunded.");
        }
        throw new DomainException(
                "Tickets could not be issued AND the refund failed. "
                        + "Please contact customer support — charge of " + chargedAmount
                        + " is pending manual reversal.");
    }

    public void validateSelectionEligibility(UUID eventId, UUID userId, String accessCode) {
        if (eventId == null) {
            throw new DomainException("Event ID is required");
        }

        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        // If the user no longer exists (in-memory repo wiped after a
        // restart, account removed, etc.) we must reject early — otherwise
        // we'd happily create an ActivePurchase that can never be completed.
        if (!userRepository.exists(userId)) {
            throw new DomainException(
                    "Your session is no longer valid. Please refresh the page and try again.");
        }

        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event does not exist");
        }

        PuchaseLottery lottery = lotteryRepository.findByEventID(eventId);

        // אירוע רגיל — לא צריך הגרלה
        if (lottery == null) {
            return;
        }

        // אירוע הגרלה — חייב קוד
        if (accessCode == null || accessCode.isBlank()) {
            throw new DomainException("Lottery access code is required for this event");
        }

        boolean valid = lottery.isAccessCodeValid(
                userId.toString(),
                accessCode,
                LocalDateTime.now()
        );

        if (!valid) {
            throw new DomainException("Invalid or expired lottery access code");
        }
    }


    public String getEventManager(UUID eventId)
    {
        Event event = eventRepository.getById(eventId);
        UUID companyId = event.getCompanyId();
        Company company = companyRepository.findByID(companyId).get();
        String founderEmail = company.getFounderEmail();
        User founder = userRepository.findByEmail(founderEmail).get();
        if (founder == null)
            throw new IllegalArgumentException();
        ICompanyMember founderRole = founder.getCompanyRole(companyId);
        return  founderRole.isMyEvent(eventId); 
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
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        List<PurchaseHistory> result = new ArrayList<>();

        for (PurchaseHistory history : historyRepository.getAll()) {
            Event event = eventRepository.getById(history.getEventId());

            if (event != null && event.getCompanyId().equals(companyId)) {
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

    /**
     * Spec invariant (general doc, page 2):
     *   "לרוכש יכולה להיות לכל היותר הזמנה פעילה אחת עבור אירוע יחיד בכל רגע נתון."
     *   A buyer may have at most one active purchase PER SINGLE EVENT at any moment.
     *
     * Different events for the same user are explicitly allowed — the 10-minute
     * auto-expiry on each ActivePurchase is what prevents abuse.
     */
    private void ensureUserHasNoOtherActivePurchaseForEvent(UUID userID, UUID eventID)
    {
        if (purchaseRepository.findByUserAndEvent(userID, eventID) != null)
            throw new DomainException("You already have an active purchase for this event. Resume or cancel it before starting a new one.");
    }

    /**
     * Read-only lookup used by the "resume in-progress purchase" flow on
     * TicketPurchase: returns the user's active purchase for this event,
     * or {@code null} if none exists or it has already expired and been
     * swept by ActivePurchaseCleaner.
     */
    public ActivePurchase findActivePurchaseByUserAndEvent(UUID userID, UUID eventID) {
        if (userID == null) {
            throw new DomainException("User ID is required");
        }
        if (eventID == null) {
            throw new DomainException("Event ID is required");
        }
        ActivePurchase purchase = purchaseRepository.findByUserAndEvent(userID, eventID);
        if (purchase == null) {
            return null;
        }
        if (purchase.isExpired(LocalDateTime.now())) {
            // Defensive: if the cleaner hasn't swept yet, don't hand a
            // dead reservation back to the client.
            return null;
        }
        return purchase;
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

        UUID companyId = event.getCompanyId();
        Company company = companyRepository.findByID(companyId).orElse(null);

        if (company == null) {
            return false;
        }

        return userRepository.findByEmail(ownerName)
                .map(u -> u.isOwnerInCompany(companyId))
                .orElse(false);
    }

    public boolean checkLastUpdate(ActivePurchase activePurchase)
    {
        return ChronoUnit.MINUTES.between(activePurchase.getLastUpdate(), LocalDateTime.now()) <= activePurchase.getMaxWaitTime();
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

        validateLotteryTicketAmount(event, ticketAmount);
        lottery.registerMember(memberId.toString(), ticketAmount, LocalDateTime.now());

        lotteryRepository.save(lottery);
    }

    private void validateLotteryTicketAmount(Event event, int ticketAmount) {
        if (event == null || event.getPurchasePolicy() == null) {
            return;
        }

        validateLotteryTicketAmountRule(
                event.getPurchasePolicy().getRulesView(),
                ticketAmount
        );
    }

    private void validateLotteryTicketAmountRule(IPurchaseRule rule, int ticketAmount) {
        if (rule == null) {
            return;
        }

        if (rule instanceof PurchaseComposite composite) {
            validateLotteryTicketAmountRule(composite.getLeftRule(), ticketAmount);
            validateLotteryTicketAmountRule(composite.getRightRule(), ticketAmount);
            return;
        }

        if (rule instanceof MinTicketRule minRule &&
                ticketAmount < minRule.getMinTicket()) {
            throw new DomainException(
                    "This event requires at least " +
                            minRule.getMinTicket() +
                            " ticket" +
                            (minRule.getMinTicket() == 1 ? "" : "s") +
                            " per registration"
            );
        }

        if (rule instanceof MaxTicketRule maxRule &&
                ticketAmount > maxRule.getMaxTicket()) {
            throw new DomainException(
                    "This event allows at most " +
                            maxRule.getMaxTicket() +
                            " ticket" +
                            (maxRule.getMaxTicket() == 1 ? "" : "s") +
                            " per registration"
            );
        }
    }



    public Map<String, String> drawLotteryForEvent(UUID eventId, LocalDateTime codeExpiry) {
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

        int availableTickets = (int) event.getTicketsView()
                .values()
                .stream()
                .filter(t -> t.getStatus() == TicketStatus.AVAILABLE)
                .count();

        Map<String, String> winnerCodes = lottery.drawWinners(availableTickets, codeExpiry);

        lotteryRepository.save(lottery);

        return winnerCodes;
    }


    public SalesReport getSalesReportForOwner(String ownerUsername, UUID companyId) {
        Company company = companyRepository.findByID(companyId).orElse(null);
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
        User owner = userRepository.findByEmail(ownerUsername).orElse(null);
        if (owner == null) {
            throw new IllegalArgumentException("Owner not found");
        }
        List<UUID> eventsUnderOwner = owner.getMyEventIdsOfCompany(companyId);
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

    public boolean isLotteryEvent(UUID eventID)
    {
        return lotteryRepository.findByEventID(eventID) != null;
    }
}