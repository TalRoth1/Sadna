package org.example.DomainLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PurchaseDomainServiceTest {

    private FakeHistoryRepository historyRepository;
    private FakeEventRepository eventRepository;
    private FakeCompanyRepository companyRepository;
    private FakeUserRepository userRepository;

    private IPurchaseRepository purchaseRepository;
    private ILotteryRepository lotteryRepository;

    private PurchaseDomainService purchaseDomainService;

    @Before
    public void setUp() {
        historyRepository = new FakeHistoryRepository();
        eventRepository = new FakeEventRepository();
        companyRepository = new FakeCompanyRepository();
        userRepository = new FakeUserRepository();

        purchaseRepository = new FakePurchaseRepository();
        lotteryRepository = new FakeLotteryRepository();

        purchaseDomainService = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );
    }

    @Test
    public void getAllHistory_whenPurchasesExist_returnsAllPurchasesWithoutFiltering() {
        PurchaseHistory firstPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 100);
        PurchaseHistory secondPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 200);
        PurchaseHistory thirdPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 300);

        historyRepository.add(firstPurchase);
        historyRepository.add(secondPurchase);
        historyRepository.add(thirdPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getAllHistory();

        assertEquals(3, result.size());
        assertTrue(result.contains(firstPurchase));
        assertTrue(result.contains(secondPurchase));
        assertTrue(result.contains(thirdPurchase));
    }

    @Test
    public void getHistoryByUser_whenMultipleUsersHavePurchases_returnsOnlyRequestedUserPurchases() {
        UUID requestedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        PurchaseHistory firstRequestedUserPurchase = purchase(requestedUserId, UUID.randomUUID(), 100);
        PurchaseHistory secondRequestedUserPurchase = purchase(requestedUserId, UUID.randomUUID(), 150);
        PurchaseHistory otherUserPurchase = purchase(otherUserId, UUID.randomUUID(), 300);

        historyRepository.add(firstRequestedUserPurchase);
        historyRepository.add(secondRequestedUserPurchase);
        historyRepository.add(otherUserPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByUser(requestedUserId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstRequestedUserPurchase));
        assertTrue(result.contains(secondRequestedUserPurchase));
        assertFalse(result.contains(otherUserPurchase));
    }

    @Test
    public void getHistoryByUser_whenUserHasNoPurchases_returnsEmptyList() {
        UUID requestedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        historyRepository.add(purchase(otherUserId, UUID.randomUUID(), 80));
        historyRepository.add(purchase(otherUserId, UUID.randomUUID(), 120));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByUser(requestedUserId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByEvent_whenMultipleEventsHavePurchases_returnsOnlyRequestedEventPurchases() {
        UUID requestedEventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();

        PurchaseHistory firstRequestedEventPurchase = purchase(UUID.randomUUID(), requestedEventId, 100);
        PurchaseHistory secondRequestedEventPurchase = purchase(UUID.randomUUID(), requestedEventId, 200);
        PurchaseHistory otherEventPurchase = purchase(UUID.randomUUID(), otherEventId, 300);

        historyRepository.add(firstRequestedEventPurchase);
        historyRepository.add(secondRequestedEventPurchase);
        historyRepository.add(otherEventPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByEvent(requestedEventId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstRequestedEventPurchase));
        assertTrue(result.contains(secondRequestedEventPurchase));
        assertFalse(result.contains(otherEventPurchase));
    }

    @Test
    public void getHistoryByEvent_whenEventHasNoPurchases_returnsEmptyList() {
        UUID requestedEventId = UUID.randomUUID();

        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 75));
        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 125));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByEvent(requestedEventId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenCompanyHasMultipleEvents_returnsPurchasesOnlyForThatCompanyEvents() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID firstCompanyEventId = UUID.randomUUID();
        UUID secondCompanyEventId = UUID.randomUUID();
        UUID otherCompanyEventId = UUID.randomUUID();

        Event firstCompanyEvent = event(firstCompanyEventId, requestedCompanyId);
        Event secondCompanyEvent = event(secondCompanyEventId, requestedCompanyId);
        Event otherCompanyEvent = event(otherCompanyEventId, otherCompanyId);

        eventRepository.save(firstCompanyEvent);
        eventRepository.save(secondCompanyEvent);
        eventRepository.save(otherCompanyEvent);

        PurchaseHistory firstCompanyPurchase = purchase(UUID.randomUUID(), firstCompanyEventId, 100);
        PurchaseHistory secondCompanyPurchase = purchase(UUID.randomUUID(), secondCompanyEventId, 200);
        PurchaseHistory otherCompanyPurchase = purchase(UUID.randomUUID(), otherCompanyEventId, 300);

        historyRepository.add(firstCompanyPurchase);
        historyRepository.add(secondCompanyPurchase);
        historyRepository.add(otherCompanyPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstCompanyPurchase));
        assertTrue(result.contains(secondCompanyPurchase));
        assertFalse(result.contains(otherCompanyPurchase));
    }

    @Test
    public void getHistoryByCompany_whenPurchaseEventDoesNotExist_ignoresThatPurchase() {
        UUID requestedCompanyId = UUID.randomUUID();

        UUID existingEventId = UUID.randomUUID();
        UUID missingEventId = UUID.randomUUID();

        eventRepository.save(event(existingEventId, requestedCompanyId));

        PurchaseHistory validPurchase = purchase(UUID.randomUUID(), existingEventId, 100);
        PurchaseHistory purchaseWithMissingEvent = purchase(UUID.randomUUID(), missingEventId, 200);

        historyRepository.add(validPurchase);
        historyRepository.add(purchaseWithMissingEvent);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(validPurchase));
        assertFalse(result.contains(purchaseWithMissingEvent));
    }

    @Test
    public void getHistoryByCompany_whenCompanyHasNoPurchases_returnsEmptyList() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID otherCompanyEventId = UUID.randomUUID();

        eventRepository.save(event(otherCompanyEventId, otherCompanyId));
        historyRepository.add(purchase(UUID.randomUUID(), otherCompanyEventId, 250));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenCompanyIdHasSameValueButDifferentObject_stillReturnsMatchingPurchases() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID sameCompanyIdDifferentObject = UUID.fromString(requestedCompanyId.toString());

        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, sameCompanyIdDifferentObject));

        PurchaseHistory purchase = purchase(UUID.randomUUID(), eventId, 400);
        historyRepository.add(purchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(purchase));
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberHasPurchases_returnsOnlyThatMemberPurchases() {
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();

        PurchaseHistory firstMemberPurchase = purchase(memberId, UUID.randomUUID(), 100);
        PurchaseHistory secondMemberPurchase = purchase(memberId, UUID.randomUUID(), 200);
        PurchaseHistory otherMemberPurchase = purchase(otherMemberId, UUID.randomUUID(), 300);

        historyRepository.add(firstMemberPurchase);
        historyRepository.add(secondMemberPurchase);
        historyRepository.add(otherMemberPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getPurchaseHistoryForMember(memberId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstMemberPurchase));
        assertTrue(result.contains(secondMemberPurchase));
        assertFalse(result.contains(otherMemberPurchase));
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberHasNoPurchases_returnsEmptyList() {
        UUID memberId = UUID.randomUUID();

        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 50));

        List<PurchaseHistory> result = purchaseDomainService.getPurchaseHistoryForMember(memberId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void eventExists_whenEventExists_returnsTrue() {
        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, UUID.randomUUID()));

        assertTrue(purchaseDomainService.eventExists(eventId));
    }

    @Test
    public void eventExists_whenEventDoesNotExist_returnsFalse() {
        assertFalse(purchaseDomainService.eventExists(UUID.randomUUID()));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenUserIsOwnerOfEventCompany_returnsTrue() {
        String ownerName = "owner";
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        Company company = mock(Company.class);

        eventRepository.save(event);
        companyRepository.save(companyId, company);

        when(company.isOwner(ownerName)).thenReturn(true);

        assertTrue(purchaseDomainService.isCompanyOwnerOfEvent(ownerName, eventId));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenUserIsNotOwnerOfEventCompany_returnsFalse() {
        String ownerName = "notOwner";
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        Company company = mock(Company.class);

        eventRepository.save(event);
        companyRepository.save(companyId, company);

        when(company.isOwner(ownerName)).thenReturn(false);

        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent(ownerName, eventId));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenEventDoesNotExist_returnsFalse() {
        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent("owner", UUID.randomUUID()));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenCompanyDoesNotExist_returnsFalse() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, companyId));

        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent("owner", eventId));
    }

    @Test
    public void getAllHistory_whenNoPurchasesExist_returnsEmptyList() {
        List<PurchaseHistory> result = purchaseDomainService.getAllHistory();

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenSomePurchasesAreForMissingOrOtherCompanyEvents_returnsOnlyMatchingCompanyPurchases() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID matchingEventId = UUID.randomUUID();
        UUID otherCompanyEventId = UUID.randomUUID();
        UUID missingEventId = UUID.randomUUID();

        eventRepository.save(event(matchingEventId, requestedCompanyId));
        eventRepository.save(event(otherCompanyEventId, otherCompanyId));

        PurchaseHistory matchingPurchase = purchase(UUID.randomUUID(), matchingEventId, 100);
        PurchaseHistory otherCompanyPurchase = purchase(UUID.randomUUID(), otherCompanyEventId, 200);
        PurchaseHistory missingEventPurchase = purchase(UUID.randomUUID(), missingEventId, 300);

        historyRepository.add(matchingPurchase);
        historyRepository.add(otherCompanyPurchase);
        historyRepository.add(missingEventPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(matchingPurchase));
        assertFalse(result.contains(otherCompanyPurchase));
        assertFalse(result.contains(missingEventPurchase));
    }

    @Test
    public void getHistoryByCompany_whenCompanyIdIsNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseDomainService.getHistoryByCompany(null)
        );
    }

    private PurchaseHistory purchase(UUID userId, UUID eventId, double total) {
        List<UUID> ticketIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        Payment payment = new Payment(total, "test-payment");

        return new PurchaseHistory(userId, ticketIds, eventId, payment);
    }

    private Event event(UUID eventId, UUID companyId) {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE
        );
    }

    private static class FakeHistoryRepository implements IHistoryRepository {

        private final List<PurchaseHistory> history = new ArrayList<>();

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
                    .filter(purchase -> purchase.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public List<PurchaseHistory> getByEventId(UUID eventId) {
            return history.stream()
                    .filter(purchase -> purchase.getEventId().equals(eventId))
                    .toList();
        }
    }

    private static class FakeEventRepository implements IEventRepository {

        private final LinkedHashMap<UUID, Event> eventsById = new LinkedHashMap<>();

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

    private static class FakeCompanyRepository implements ICompanyRepository {

        private final LinkedHashMap<UUID, Company> companiesById = new LinkedHashMap<>();

        public void save(UUID companyId, Company company) {
            companiesById.put(companyId, company);
        }

        @Override
        public UUID createCompany(String founderUsername, String companyName) {
            return null;
        }

        @Override
        public Optional<Company> findByID(UUID companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public boolean isOwner(String username, UUID companyId) {
            Company company = companiesById.get(companyId);
            return company != null && company.isOwner(username);
        }

        @Override
        public void save(Company company) {
            companiesById.put(company.getId(), company);
        }

        @Override
        public List<Company> getCompaniesByMember(String username) {
            return companiesById.values().stream()
                    .filter(company -> company.hasMember(username))
                    .toList();
        }
    }

    private static class FakeUserRepository implements IUserRepository {

        @Override
        public void add(User user) {
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return Optional.empty();
        }

        @Override
        public boolean exists(UUID userId) {
            return false;
        }

        @Override
        public boolean isSystemAdmin(String username) {
            return false;
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return false;
        }
    }

    private static class FakePurchaseRepository implements IPurchaseRepository {

        @Override
        public ActivePurchase findByUserID(UUID userID) {
            return null;
        }

        @Override
        public ActivePurchase findByID(UUID purchaseID) {
            return null;
        }

        @Override
        public void save(ActivePurchase activePurchase) {
        }

        @Override
        public void deleteByID(UUID activePurchaseID) {
        }
    }

    private static class FakeLotteryRepository implements ILotteryRepository {

        @Override
        public void save(PuchaseLottery lottery) {
        }

        @Override
        public PuchaseLottery findByID(UUID lotteryId) {
            return null;
        }

        @Override
        public PuchaseLottery findByEventID(UUID eventId) {
            return null;
        }
    }
}