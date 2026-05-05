package org.example.ApplicationLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.example.DomainLayer.DomainException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

    @Mock
    private IEventRepository eventRepositoryMock;
    @Mock
    private ICompanyRepository companyRepositoryMock;
    @Mock
    private IHistoryRepository historyRepositoryMock;

    private EventService eventService;
    private EventManagementDomainService eventManagementDomainService;

    @Before
    public void setUp() {
        // Using real Domain Service logic to track state changes in the Event object
        eventManagementDomainService = new EventManagementDomainService(
                eventRepositoryMock, 
                historyRepositoryMock, 
                companyRepositoryMock
        );
        eventService = new EventService(eventManagementDomainService);
    }

    private Event createTestEvent(UUID companyId) {
        return new Event(
            UUID.randomUUID(), 
            companyId, 
            LocalDateTime.now().plusDays(10), 
            "Tel Aviv", 
            "Artist Name", 
            "Concert", 
            EventStatus.ACTIVE
        );
    }

    @Test
    public void testAddPolicyRule_ActuallyPersistsInEvent() {
        // Arrange
        Company company = new Company("founderUsername", "testComp");
        Event realEvent = createTestEvent(company.getId());
        UUID eventId = realEvent.getEventId();
        when(eventRepositoryMock.getById(eventId)).thenReturn(realEvent);
        when(companyRepositoryMock.findByID(company.getId())).thenReturn(Optional.ofNullable(company));

        // Act
        eventService.addPolicyRule("founderUsername", company.getId(), eventId, Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(true));

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        assertEquals("Should have 2 rules added", 2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
    }

    @Test
    public void testAddMultiplePolicyRules_ReplacementLogic() {
        // Arrange
        Company company = new Company("founderUsername", "testComp");
        Event realEvent = createTestEvent(company.getId());
        UUID eventId = realEvent.getEventId();
        when(eventRepositoryMock.getById(eventId)).thenReturn(realEvent);
        when(companyRepositoryMock.findByID(company.getId())).thenReturn(Optional.ofNullable(company));

        // Act: Add 18+, then update to 21+
        eventService.addPolicyRule("founderUsername", company.getId(), eventId, Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty());
        eventService.addPolicyRule("founderUsername", company.getId(), eventId, Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty());

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        long ageRuleCount = rules.stream().filter(r -> r instanceof AgeRule).count();
        
        assertEquals("Should only have one AgeRule", 1, ageRuleCount);
        assertEquals(21.0f, ((AgeRule)rules.get(0)).getMinAge(), 0.01);
    }

    @Test
    public void testDeleteSpecificPolicyRules() {
        // Arrange
        Company company = new Company("founderUsername", "testComp");
        Event realEvent = createTestEvent(company.getId());
        UUID eventId = realEvent.getEventId();
        when(eventRepositoryMock.getById(eventId)).thenReturn(realEvent);
        when(companyRepositoryMock.findByID(company.getId())).thenReturn(Optional.ofNullable(company));
        // Setup initial state with two rules
        realEvent.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(false));

        // Act: Delete AgeRule, keep LoneSeatRule
        eventService.deletePolicyRule("founderUsername", company.getId(), eventId, true, false, false, false);

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        assertEquals("Should have 1 rule remaining", 1, rules.size());
        assertTrue("Remaining rule should be LoneSeatRule", rules.get(0) instanceof LoneSeatRule);
    }

    @Test
    public void testAddOvertDiscount_VerifyStatePersistence() {
        // Arrange
        Company company = new Company("founderUsername", "testComp");
        Event realEvent = createTestEvent(company.getId());
        UUID eventId = realEvent.getEventId();
        when(eventRepositoryMock.getById(eventId)).thenReturn(realEvent);
        when(companyRepositoryMock.findByID(company.getId())).thenReturn(Optional.ofNullable(company));

        // Act
        eventService.addOvertDiscount("founderUsername", company.getId(), eventId, LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);

        // Assert
        var discounts = realEvent.getDiscountPolicy().gDiscountRules();
        assertFalse("Discount list should not be empty", discounts.isEmpty());
        assertTrue("Discount should be OvertDiscount type", discounts.get(0) instanceof OvertDiscount);
    }

    @Test
    public void testRemoveDiscount_VerifyRemovalByID() {
        // Arrange
        Company company = new Company("founderUsername", "testComp");
        Event realEvent = createTestEvent(company.getId());
        UUID eventId = realEvent.getEventId();
        when(eventRepositoryMock.getById(eventId)).thenReturn(realEvent);
        when(companyRepositoryMock.findByID(company.getId())).thenReturn(Optional.ofNullable(company));

        // Add a discount to get an ID
        realEvent.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);
        UUID discountId = realEvent.getDiscountPolicy().gDiscountRules().get(0).getId();

        // Act
        eventService.removeDiscount("founderUsername", company.getId(), eventId, discountId);

        // Assert
        assertTrue("Discount list should be empty after removal", 
                    realEvent.getDiscountPolicy().gDiscountRules().isEmpty());
    }

    @Test
    public void eventRate_success()
    {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID eventID = UUID.randomUUID();

        Event event = new Event(eventID, UUID.randomUUID(), LocalDateTime.now(), "sdsdsdsd", "sdsdsdsd", "sdsdsdsd", EventStatus.ACTIVE);

        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        eventRepository.save(event);

        EventManagementDomainService eventManagementDomainService = new EventManagementDomainService(eventRepository, null, null);
        EventService eventService = new EventService(eventManagementDomainService);

        eventService.rateEvent(user1, eventID, 5);
        eventService.rateEvent(user2, eventID, 1);

        assertTrue(3 == event.getRating());
    }
    @Test
    public void eventRate_samePerson_thenItFails()
    {
        UUID user1 = UUID.randomUUID();

        UUID eventID = UUID.randomUUID();

        Event event = new Event(eventID, UUID.randomUUID(), LocalDateTime.now(), "sdsdsdsd", "sdsdsdsd", "sdsdsdsd", EventStatus.ACTIVE);

        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        eventRepository.save(event);

        EventManagementDomainService eventManagementDomainService = new EventManagementDomainService(eventRepository, null, null);
        EventService eventService = new EventService(eventManagementDomainService);

        eventService.rateEvent(user1, eventID, 5);
        assertThrows(DomainException.class, () -> eventService.rateEvent(user1, eventID, 1));

        assertTrue(5 == event.getRating());
    }

    private static class InMemoryEventRepository implements IEventRepository
    {
        Map<UUID, Event> eventsByID = new LinkedHashMap<>();

        @Override
        public Event getById(UUID eventId) {
            return eventsByID.get(eventId);
        }

        @Override
        public List<Event> getAll() {
            return eventsByID.values().stream().toList();
        }

        @Override
        public void save(Event event) {
            eventsByID.put(event.getEventId(), event);
        }

        @Override
        public void delete(UUID eventId) {
            eventsByID.remove(eventId);
        }
    }
}