package org.example.DomainLayer;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentResult;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.example.DomainLayer.AdminAggregate.Admin;

/**
 * Concurrency and checkout invariant tests for PurchaseDomainService.
 *
 * These tests intentionally start at the Domain service instead of the React/API
 * layer. They pin down the important business invariants around reservation,
 * checkout, external payment/ticketing gateways and rollback/compensation.
 */
public class PurchaseDomainConcurrencyAndCheckoutTest {

    private ThreadSafeHistoryRepository historyRepository;
    private ThreadSafeEventRepository eventRepository;
    private ThreadSafePurchaseRepository purchaseRepository;
    private ThreadSafeCompanyRepository companyRepository;
    private ThreadSafeUserRepository userRepository;
    private ThreadSafeLotteryRepository lotteryRepository;
    private PurchaseDomainService purchaseDomainService;

    @Before
    public void setUp() {
        historyRepository = new ThreadSafeHistoryRepository();
        eventRepository = new ThreadSafeEventRepository();
        purchaseRepository = new ThreadSafePurchaseRepository();
        companyRepository = new ThreadSafeCompanyRepository();
        userRepository = new ThreadSafeUserRepository();
        lotteryRepository = new ThreadSafeLotteryRepository();

        purchaseDomainService = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );
    }

    // ---------------------------------------------------------------------
    // Reservation concurrency tests
    // ---------------------------------------------------------------------

    @Test
    public void concurrentReserveSameSittingTicket_onlyOneUserGetsActivePurchaseAndTicketIsReservedOnce()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, 100f));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);
        addUser(user1Id, "user1");
        addUser(user2Id, "user2");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(reserveSittingTask(start, eventId, List.of(ticketId), user1Id));
            Future<Boolean> second = executor.submit(reserveSittingTask(start, eventId, List.of(ticketId), user2Id));

            start.countDown();

            int successes = boolToInt(first.get(5, TimeUnit.SECONDS))
                    + boolToInt(second.get(5, TimeUnit.SECONDS));

            assertEquals(1, successes);
            assertEquals(1, purchaseRepository.findAll().size());
            assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());

            long purchasesHoldingTicket = purchaseRepository.findAll().stream()
                    .filter(purchase -> purchase.getTicketIDs().containsKey(ticketId))
                    .count();
            assertEquals(1, purchasesHoldingTicket);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentReserveStandingTickets_moreUsersThanCapacity_onlyCapacityUsersSucceed()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        int capacity = 3;
        int contenders = 12;

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new StandingArea(areaId, 75f));
        event.addStandingTickets(areaId, capacity);
        eventRepository.save(event);

        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            UUID userId = UUID.randomUUID();
            addUser(userId, "standing-user-" + i);
            userIds.add(userId);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (UUID userId : userIds) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        purchaseDomainService.selectStandingTickets(eventId, 1, userId, areaId, false);
                        return true;
                    } catch (DomainException expectedWhenSoldOut) {
                        return false;
                    }
                }));
            }

            start.countDown();

            int successes = collectSuccesses(futures);
            assertEquals(capacity, successes);
            assertEquals(capacity, purchaseRepository.findAll().size());
            assertEquals(capacity, countTicketsByStatus(event, TicketStatus.RESERVED));
            assertEquals(0, countTicketsByStatus(event, TicketStatus.AVAILABLE));

            Set<UUID> reservedTicketIds = new LinkedHashSet<>();
            for (ActivePurchase purchase : purchaseRepository.findAll()) {
                assertEquals(1, purchase.getTicketIDs().size());
                UUID reservedTicketId = purchase.getTicketIDs().keySet().iterator().next();
                assertTrue("same standing ticket must not be assigned to two buyers",
                        reservedTicketIds.add(reservedTicketId));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void concurrentReserveDifferentSittingTickets_allUsersSucceedAndEachTicketReservedOnce()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        int ticketCount = 20;

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, 120f));

        List<UUID> ticketIds = new ArrayList<>();
        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < ticketCount; i++) {
            UUID ticketId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ticketIds.add(ticketId);
            userIds.add(userId);
            event.addTicket(new SittingTicket(ticketId, eventId, areaId, 120f, 1, i + 1));
            addUser(userId, "sitting-user-" + i);
        }
        eventRepository.save(event);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(ticketCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < ticketCount; i++) {
                futures.add(executor.submit(
                        reserveSittingTask(start, eventId, List.of(ticketIds.get(i)), userIds.get(i))
                ));
            }

            start.countDown();

            assertEquals(ticketCount, collectSuccesses(futures));
            assertEquals(ticketCount, purchaseRepository.findAll().size());
            assertEquals(ticketCount, countTicketsByStatus(event, TicketStatus.RESERVED));

            Set<UUID> allReservedIds = new LinkedHashSet<>();
            for (ActivePurchase purchase : purchaseRepository.findAll()) {
                assertEquals(1, purchase.getTicketIDs().size());
                assertTrue(allReservedIds.add(purchase.getTicketIDs().keySet().iterator().next()));
            }
            assertEquals(ticketCount, allReservedIds.size());
        } finally {
            executor.shutdownNow();
        }
    }


    // ---------------------------------------------------------------------
    // Complete purchase success and failure invariant tests
    // ---------------------------------------------------------------------

    @Test
    public void completePurchase_whenPaymentAndTicketingSucceed_sellsTicketClosesActivePurchaseAndAddsHistory() {
        PurchaseScenario scenario = createReservedSittingPurchase(100f);
        RecordingPaymentGateway paymentGateway = new RecordingPaymentGateway(true);
        RecordingTicketingGateway ticketingGateway = new RecordingTicketingGateway(false);
        purchaseDomainService.setPaymentGateway(paymentGateway);
        purchaseDomainService.setTicketingGateway(ticketingGateway);

        boolean soldOut = purchaseDomainService.completePurchase(
                scenario.activePurchase.getActivePurchaseId(),
                new PaymentDetails(),
                null
        );

        assertTrue(soldOut);
        assertEquals(1, paymentGateway.payCalls.get());
        assertEquals(1, ticketingGateway.issueCalls.get());
        assertEquals(Set.of(scenario.ticketId), ticketingGateway.lastIssuedTicketIds);
        assertEquals(TicketStatus.SOLD, scenario.event.getTicket(scenario.ticketId).getStatus());
        assertNull(purchaseRepository.findByID(scenario.activePurchase.getActivePurchaseId()));

        List<PurchaseHistory> history = historyRepository.getByUserId(scenario.userId);
        assertEquals(1, history.size());
        assertEquals(scenario.eventId, history.get(0).getEventId());
        assertEquals(scenario.userId, history.get(0).getUserId());
        assertTrue(history.get(0).getTicketIds().contains(scenario.ticketId));
    }

    @Test
    public void completePurchase_whenPaymentDeclined_doesNotIssueTicketsDoesNotSellAndKeepsActivePurchaseForRetry() {
        PurchaseScenario scenario = createReservedSittingPurchase(100f);
        RecordingPaymentGateway paymentGateway = new RecordingPaymentGateway(false);
        RecordingTicketingGateway ticketingGateway = new RecordingTicketingGateway(false);
        purchaseDomainService.setPaymentGateway(paymentGateway);
        purchaseDomainService.setTicketingGateway(ticketingGateway);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        scenario.activePurchase.getActivePurchaseId(),
                        new PaymentDetails(),
                        null
                )
        );

        assertEquals(1, paymentGateway.payCalls.get());
        assertEquals(0, paymentGateway.refundCalls.get());
        assertEquals(0, ticketingGateway.issueCalls.get());
        assertEquals(TicketStatus.RESERVED, scenario.event.getTicket(scenario.ticketId).getStatus());
        assertNotNull(purchaseRepository.findByID(scenario.activePurchase.getActivePurchaseId()));
        assertTrue(historyRepository.getByUserId(scenario.userId).isEmpty());
    }

    @Test
    public void completePurchase_whenTicketingFailsAfterPayment_refundsReleasesTicketDeletesActivePurchaseAndDoesNotAddHistory() {
        PurchaseScenario scenario = createReservedSittingPurchase(100f);
        RecordingPaymentGateway paymentGateway = new RecordingPaymentGateway(true);
        RecordingTicketingGateway ticketingGateway = new RecordingTicketingGateway(true);
        purchaseDomainService.setPaymentGateway(paymentGateway);
        purchaseDomainService.setTicketingGateway(ticketingGateway);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        scenario.activePurchase.getActivePurchaseId(),
                        new PaymentDetails(),
                        null
                )
        );

        assertEquals(1, paymentGateway.payCalls.get());
        assertEquals(1, ticketingGateway.issueCalls.get());
        assertEquals(1, paymentGateway.refundCalls.get());
        assertEquals(TicketStatus.AVAILABLE, scenario.event.getTicket(scenario.ticketId).getStatus());
        assertNull(purchaseRepository.findByID(scenario.activePurchase.getActivePurchaseId()));
        assertTrue(historyRepository.getByUserId(scenario.userId).isEmpty());
    }

    @Test
    public void completePurchase_whenActivePurchaseEndTimeExpired_releasesReservationDeletesPurchaseAndSkipsExternalGateways() {
        PurchaseScenario scenario = createManuallyExpiredReservedPurchase(100f);
        RecordingPaymentGateway paymentGateway = new RecordingPaymentGateway(true);
        RecordingTicketingGateway ticketingGateway = new RecordingTicketingGateway(false);
        purchaseDomainService.setPaymentGateway(paymentGateway);
        purchaseDomainService.setTicketingGateway(ticketingGateway);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        scenario.activePurchase.getActivePurchaseId(),
                        new PaymentDetails(),
                        null
                )
        );

        assertEquals(0, paymentGateway.payCalls.get());
        assertEquals(0, ticketingGateway.issueCalls.get());
        assertEquals(TicketStatus.AVAILABLE, scenario.event.getTicket(scenario.ticketId).getStatus());
        assertNull(purchaseRepository.findByID(scenario.activePurchase.getActivePurchaseId()));
        assertTrue(historyRepository.getByUserId(scenario.userId).isEmpty());
    }

    @Test
    public void completePurchase_whenTenMinuteInactivityWindowPassed_cancelsPurchaseAndSkipsExternalGateways()
            throws Exception {
        PurchaseScenario scenario = createReservedSittingPurchase(100f);
        setLastUpdate(scenario.activePurchase, LocalDateTime.now().minusMinutes(11));

        RecordingPaymentGateway paymentGateway = new RecordingPaymentGateway(true);
        RecordingTicketingGateway ticketingGateway = new RecordingTicketingGateway(false);
        purchaseDomainService.setPaymentGateway(paymentGateway);
        purchaseDomainService.setTicketingGateway(ticketingGateway);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        scenario.activePurchase.getActivePurchaseId(),
                        new PaymentDetails(),
                        null
                )
        );

        assertEquals(0, paymentGateway.payCalls.get());
        assertEquals(0, ticketingGateway.issueCalls.get());
        assertEquals(TicketStatus.AVAILABLE, scenario.event.getTicket(scenario.ticketId).getStatus());
        assertNull(purchaseRepository.findByID(scenario.activePurchase.getActivePurchaseId()));
        assertTrue(historyRepository.getByUserId(scenario.userId).isEmpty());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Callable<Boolean> reserveSittingTask(
            CountDownLatch start,
            UUID eventId,
            List<UUID> ticketIds,
            UUID userId
    ) {
        return () -> {
            start.await();
            try {
                purchaseDomainService.selectSittingTickets(eventId, ticketIds, userId, false);
                return true;
            } catch (DomainException expectedContentionFailure) {
                return false;
            }
        };
    }

    private int collectSuccesses(List<Future<Boolean>> futures) throws Exception {
        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(5, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        return successes;
    }

    private int boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    private Event event(UUID eventId, UUID companyId) {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE,
                DiscountType.ALL
        );
    }

    private void addUser(UUID userId, String username) {
        userRepository.add(new User(userId, username, username + "@mail.test", "hash", 25));
    }

    private int countTicketsByStatus(Event event, TicketStatus status) {
        int count = 0;
        for (Ticket ticket : event.getTicketsView().values()) {
            if (ticket.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private PurchaseScenario createReservedSittingPurchase(float price) {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, price));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, price, 1, 1));
        eventRepository.save(event);
        addUser(userId, "buyer");

        ActivePurchase activePurchase = purchaseDomainService.selectSittingTickets(
                eventId,
                List.of(ticketId),
                userId,
                false
        );

        return new PurchaseScenario(eventId, userId, ticketId, event, activePurchase);
    }

    private PurchaseScenario createManuallyExpiredReservedPurchase(float price) {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, price));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, price, 1, 1));
        event.reserveSittingTickets(List.of(ticketId));
        eventRepository.save(event);
        addUser(userId, "expired-buyer");

        LinkedHashMap<UUID, Float> ticketPrices = new LinkedHashMap<>();
        ticketPrices.put(ticketId, price);
        ActivePurchase expiredPurchase = new ActivePurchase(
                userId,
                eventId,
                ticketPrices,
                LocalDateTime.now().minusSeconds(1)
        );
        purchaseRepository.save(expiredPurchase);

        return new PurchaseScenario(eventId, userId, ticketId, event, expiredPurchase);
    }

    private void setLastUpdate(ActivePurchase purchase, LocalDateTime lastUpdate) throws Exception {
        Field field = ActivePurchase.class.getDeclaredField("lastUpdate");
        field.setAccessible(true);
        field.set(purchase, lastUpdate);
    }

    private record PurchaseScenario(
            UUID eventId,
            UUID userId,
            UUID ticketId,
            Event event,
            ActivePurchase activePurchase
    ) {
    }

    private static class RecordingPaymentGateway implements IPaymentGateway {
        private final boolean approvePayments;
        private final AtomicInteger payCalls = new AtomicInteger();
        private final AtomicInteger refundCalls = new AtomicInteger();

        private RecordingPaymentGateway(boolean approvePayments) {
            this.approvePayments = approvePayments;
        }

        @Override
        public PaymentResult pay(UUID userID, float amount, PaymentDetails paymentDetails) {
            payCalls.incrementAndGet();

            if (!approvePayments) {
                return PaymentResult.failure();
            }

            return PaymentResult.success(10000);
        }

        @Override
        public boolean refund(int transactionId) {
            refundCalls.incrementAndGet();
            return true;
        }
    }
    private static class RecordingTicketingGateway implements ITicketingGateway {
        private final boolean failIssue;
        private final AtomicInteger issueCalls = new AtomicInteger();
        private Set<UUID> lastIssuedTicketIds = Collections.emptySet();

        private RecordingTicketingGateway(boolean failIssue) {
            this.failIssue = failIssue;
        }

        @Override
        public String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
            issueCalls.incrementAndGet();
            lastIssuedTicketIds = new LinkedHashSet<>(ticketIds);

            if (failIssue) {
                throw new RuntimeException("ticketing gateway failed");
            }

            return "SIM-TICKET";
        }
    }
    private static class ThreadSafeHistoryRepository implements IHistoryRepository {
        private final List<PurchaseHistory> history = new CopyOnWriteArrayList<>();

        @Override
        public void add(PurchaseHistory purchaseHistory) {
            history.add(purchaseHistory);
        }

        @Override
        public List<PurchaseHistory> getAll() {
            return new ArrayList<>(history);
        }

        @Override
        public List<PurchaseHistory> getByUserId(UUID userId) {
            return history.stream()
                    .filter(purchase -> userId.equals(purchase.getUserId()))
                    .toList();
        }

        @Override
        public List<PurchaseHistory> getByEventId(UUID eventId) {
            return history.stream()
                    .filter(purchase -> eventId.equals(purchase.getEventId()))
                    .toList();
        }
    }

    private static class ThreadSafeEventRepository implements IEventRepository {
        private final Map<UUID, Event> eventsById = new ConcurrentHashMap<>();

        @Override
        public Event getById(UUID eventId) {
            return eventsById.get(eventId);
        }

        @Override
        public List<Event> getAll() {
            return new ArrayList<>(eventsById.values());
        }

        @Override
        public void save(Event event) {
            eventsById.put(event.getEventId(), event);
        }

        @Override
        public void delete(UUID eventId) {
            eventsById.remove(eventId);
        }
    }

    private static class ThreadSafePurchaseRepository implements IPurchaseRepository {
        private final Map<UUID, ActivePurchase> purchasesById = new ConcurrentHashMap<>();

        @Override
        public ActivePurchase findByUserID(UUID userID) {
            return purchasesById.values().stream()
                    .filter(purchase -> userID.equals(purchase.getUserID()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ActivePurchase findByUserAndEvent(UUID userID, UUID eventID) {
            return purchasesById.values().stream()
                    .filter(purchase -> userID.equals(purchase.getUserID()))
                    .filter(purchase -> eventID.equals(purchase.getEventID()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ActivePurchase findByID(UUID purchaseID) {
            return purchasesById.get(purchaseID);
        }

        @Override
        public void save(ActivePurchase activePurchase) {
            purchasesById.put(activePurchase.getActivePurchaseId(), activePurchase);
        }

        @Override
        public void deleteByID(UUID activePurchaseID) {
            purchasesById.remove(activePurchaseID);
        }

        @Override
        public List<ActivePurchase> findAll() {
            return new ArrayList<>(purchasesById.values());
        }
    }

    private static class ThreadSafeCompanyRepository implements ICompanyRepository {
        private final Map<UUID, Company> companiesById = new ConcurrentHashMap<>();

        @Override
        public UUID createCompany(String founderUsername, String companyName, DiscountType discountType) {
            Company company = new Company(founderUsername, companyName, discountType);
            save(company);
            return company.getId();
        }

        @Override
        public Optional<Company> findByID(UUID companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public void save(Company company) {
            companiesById.put(company.getId(), company);
        }

        @Override
        public List<Company> getAllActive() {
            return companiesById.values().stream()
                    .filter(Company::isActive)
                    .toList();
        }

        @Override
        public List<Company> getAll() {
            return new ArrayList<>(companiesById.values());
        }
    }

    private static class ThreadSafeUserRepository implements IUserRepository {
        private final Map<UUID, User> usersById = new ConcurrentHashMap<>();
        private final Map<UUID, Admin> adminsById = new ConcurrentHashMap<>();
        private final Map<String, UUID> adminIdsByUsername = new ConcurrentHashMap<>();

        @Override
        public void add(User user) {
            usersById.put(user.getId(), user);
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return Optional.ofNullable(usersById.get(UID));
        }

        @Override
        public boolean exists(UUID userId) {
            return usersById.containsKey(userId);
        }

        @Override
        public boolean existsByUsername(String username) {
            return usersById.values().stream()
                    .anyMatch(user -> username.equals(user.getUsername()));
        }


        @Override
        public boolean existsByEmail(String email) {
            return usersById.values().stream()
                    .anyMatch(user -> email.equals(user.getEmail()));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return usersById.values().stream()
                    .filter(user -> email.equals(user.getEmail()) || email.equals(user.getUsername()))
                    .findFirst();
        }

        @Override
        public List<UUID> getCompaniesIdsByMember(String username) {
            return List.of();
        }

        @Override
        public boolean isCompanyOwner(String username, UUID companyId) {
            return false;
        }

        @Override
        public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId) {
            return false;
        }

        @Override
        public Map<UUID, User> getAllUsers() {
            return Collections.unmodifiableMap(usersById);
        }

        @Override
        public void addAdmin(Admin admin) {
            if (admin == null || admin.getId() == null || admin.getUsername() == null) {
                return;
            }

            String username = admin.getUsername().trim().toLowerCase();
            adminsById.put(admin.getId(), admin);
            adminIdsByUsername.put(username, admin.getId());
        }

        @Override
        public boolean isSystemAdmin(String username) {
            if (username == null) {
                return false;
            }

            return adminIdsByUsername.containsKey(username.trim().toLowerCase());
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return adminId != null && adminsById.containsKey(adminId);
        }
    }

    private static class ThreadSafeLotteryRepository implements ILotteryRepository {
        private final Map<UUID, PuchaseLottery> lotteriesById = new ConcurrentHashMap<>();
        private final Map<UUID, PuchaseLottery> lotteriesByEventId = new ConcurrentHashMap<>();

        @Override
        public void save(PuchaseLottery lottery) {
            lotteriesById.put(lottery.getLotteryId(), lottery);
            lotteriesByEventId.put(lottery.getEventId(), lottery);
        }

        @Override
        public PuchaseLottery findByID(UUID lotteryId) {
            return lotteriesById.get(lotteryId);
        }

        @Override
        public PuchaseLottery findByEventID(UUID eventId) {
            return lotteriesByEventId.get(eventId);
        }

        @Override
        public List<PuchaseLottery> findAll() {
            return new ArrayList<>(lotteriesById.values());
        }

        @Override
        public List<UUID> findEventIdsReadyForDraw(LocalDateTime now) {
            return Collections.emptyList();
        }
    }
}
