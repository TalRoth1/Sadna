package org.example.ApplicationLayer;

import org.example.ApplicationLayer.EventDtos.AreaSummaryDto;
import org.example.ApplicationLayer.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.EventDtos.EventSummaryDto;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.IDiscountRule;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.InfrastructureLayer.InMemoryEventRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

    // --- Mocks ---
    @Mock
    private IEventRepository eventRepository;
    @Mock
    private ICompanyRepository companyRepository;
    @Mock
    private IHistoryRepository historyRepository;

    // --- Services ---
    private EventService eventService;
    private EventManagementDomainService eventManagementDomainService;

    // --- Test Data Fields ---
    private UUID eventId;
    private UUID userId;
    private UUID companyId;
    private UUID discountId;
    private String ownerUsername;

    @Before
    public void setUp() {
        // Using real Domain Service logic to track state changes in the Event object
        eventManagementDomainService = new EventManagementDomainService(
                eventRepository, 
                historyRepository, 
                companyRepository
        );
        eventService = new EventService(eventManagementDomainService);

        // Fields from the second test file
        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        discountId = UUID.randomUUID();
        ownerUsername = "owner-user";
    }

    // =====================================================================
    //                   Helper Methods
    // =====================================================================

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

    private Event newRealEvent() {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(30),
                "Tel Aviv",
                "Some Artist",
                "concert",
                EventStatus.ACTIVE);
    }

    private Company newActiveCompany(String name) {
        return new Company("founder-" + name, name);
    }

    private Event newEventFor(UUID companyId, String name, String artist, String type, 
                              LocalDateTime date, String location, EventStatus status, String... tags) {
        Event e = new Event(UUID.randomUUID(), companyId, date, location, artist, type, status);
        if (name != null) {
            e.setName(name);
        }
        for (String t : tags) {
            e.addTag(t);
        }
        return e;
    }

    private void addStandingArea(Event e, double price) {
        e.getLayout().addArea(new StandingArea(UUID.randomUUID(), price));
    }

    private void addSittingArea(Event e, double price) {
        e.getLayout().addArea(new SittingArea(UUID.randomUUID(), price));
    }

    // =====================================================================
    //                   Tests from Original File
    // =====================================================================

    @Test
    public void testAddPolicyRule_ActuallyPersistsInEvent() {
        // Arrange
        Event realEvent = createTestEvent(UUID.randomUUID());
        UUID eventId = realEvent.getEventId();
        when(eventRepository.getById(eventId)).thenReturn(Optional.of(realEvent).get());

        // Act
        eventService.addPolicyRule(eventId, Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(true));

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        assertEquals("Should have 2 rules added", 2, rules.size());
        assertTrue(rules.stream().anyMatch(r -> r instanceof AgeRule));
        assertTrue(rules.stream().anyMatch(r -> r instanceof LoneSeatRule));
    }

    @Test
    public void testAddMultiplePolicyRules_ReplacementLogic() {
        // Arrange
        Event realEvent = createTestEvent(UUID.randomUUID());
        UUID eventId = realEvent.getEventId();
        when(eventRepository.getById(eventId)).thenReturn(Optional.of(realEvent).get());

        // Act: Add 18+, then update to 21+
        eventService.addPolicyRule(eventId, Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty());
        eventService.addPolicyRule(eventId, Optional.of(21.0f), Optional.empty(), Optional.empty(), Optional.empty());

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        long ageRuleCount = rules.stream().filter(r -> r instanceof AgeRule).count();
        
        assertEquals("Should only have one AgeRule", 1, ageRuleCount);
        assertEquals(21.0f, ((AgeRule)rules.get(0)).getMinAge(), 0.01);
    }

    @Test
    public void testDeleteSpecificPolicyRules() {
        // Arrange
        Event realEvent = createTestEvent(UUID.randomUUID());
        UUID eventId = realEvent.getEventId();
        // Setup initial state with two rules
        realEvent.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.of(false));
        when(eventRepository.getById(eventId)).thenReturn(Optional.of(realEvent).get());

        // Act: Delete AgeRule, keep LoneSeatRule
        eventService.deletePolicyRule(eventId, true, false, false, false);

        // Assert
        var rules = realEvent.getPurchasePolicy().getRulesView();
        assertEquals("Should have 1 rule remaining", 1, rules.size());
        assertTrue("Remaining rule should be LoneSeatRule", rules.get(0) instanceof LoneSeatRule);
    }

    @Test
    public void testAddOvertDiscount_VerifyStatePersistence() {
        // Arrange
        Event realEvent = createTestEvent(UUID.randomUUID());
        UUID eventId = realEvent.getEventId();
        when(eventRepository.getById(eventId)).thenReturn(Optional.of(realEvent).get());

        // Act
        eventService.addOvertDiscount(eventId, LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);

        // Assert
        var discounts = realEvent.getDiscountPolicy().gDiscountRules();
        assertFalse("Discount list should not be empty", discounts.isEmpty());
        assertTrue("Discount should be OvertDiscount type", discounts.get(0) instanceof OvertDiscount);
    }

    @Test
    public void testRemoveDiscount_VerifyRemovalByID() {
        // Arrange
        Event realEvent = createTestEvent(UUID.randomUUID());
        UUID eventId = realEvent.getEventId();
        when(eventRepository.getById(eventId)).thenReturn(Optional.of(realEvent).get());

        // Add a discount to get an ID
        realEvent.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 20.0f);
        UUID discountId = realEvent.getDiscountPolicy().gDiscountRules().get(0).getId();

        // Act
        eventService.removeDiscount(eventId, discountId);

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
        // EventService swallows DomainException on duplicate rating, so the second
        // call must complete normally and the original rating must remain unchanged.
        try {
            eventService.rateEvent(user1, eventID, 1);
        } catch (Throwable t) {
            fail("expected no exception to escape the service, but got: " + t);
        }

        assertTrue(5 == event.getRating());
    }

    // =====================================================================
    //                   Tests from New File
    // =====================================================================

    @Test
    public void GivenAuthorizedOwnerAndExistingEvent_WhenGetEventPurchaseHistoryForOwner_ThenReturnsHistoryFromRepository() {
        // Arrange
        Event event = newRealEvent();
        List<PurchaseHistory> expected = Collections.emptyList();
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.isOwner(ownerUsername, companyId)).thenReturn(true);
        when(historyRepository.getByEventId(eventId)).thenReturn(expected);

        // Act
        List<PurchaseHistory> actual =
                eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId);

        // Assert
        assertSame("service must return the exact list from the repository", expected, actual);
        verify(eventRepository).getById(eventId);
        verify(companyRepository).isOwner(ownerUsername, companyId);
        verify(historyRepository).getByEventId(eventId);
    }

    @Test
    public void GivenNullOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(null, eventId));
        assertTrue("message should reference the offending parameter",
                ex.getMessage().toLowerCase().contains("owner"));
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(historyRepository);
        verifyNoInteractions(companyRepository);
    }

    @Test
    public void GivenEmptyOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner("", eventId));
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(historyRepository);
        verifyNoInteractions(companyRepository);
    }

    @Test
    public void GivenBlankOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner("   ", eventId));
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(historyRepository);
        verifyNoInteractions(companyRepository);
    }

    @Test
    public void GivenEventDoesNotExist_WhenGetEventPurchaseHistoryForOwner_ThenThrowsDomainException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertEquals("Event not found", ex.getMessage());
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    @Test
    public void GivenUserIsNotOwnerOfEventCompany_WhenGetEventPurchaseHistoryForOwner_ThenThrowsDomainException() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.isOwner(ownerUsername, companyId)).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertTrue("message should mention authorization",
                ex.getMessage().toLowerCase().contains("not authorized"));
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    @Test
    public void GivenAllOptionalsPresentAndValid_WhenAddPolicyRule_ThenAllFourRulesAreAddedToTheEventPolicy() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));

        assertEquals("expected one rule per provided optional",
                4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAllOptionalsEmpty_WhenAddPolicyRule_ThenNoRuleIsAddedToTheEventPolicy() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals("an empty optional means 'no rule for this field'",
                0, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenOnlyAgeIsPresent_WhenAddPolicyRule_ThenExactlyTheAgeRuleIsAdded() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.of(21f), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals("only the age rule must be added when other optionals are empty",
                1, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAgeIsZero_WhenAddPolicyRule_ThenServiceAcceptsItAndDelegates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.of(0f), Optional.of(1), Optional.of(5), Optional.of(true));

        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenMinTicketIsZero_WhenAddPolicyRule_ThenServiceAcceptsItAndDelegates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.of(18f), Optional.of(0), Optional.of(5), Optional.of(true));

        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenMaxTicketIsZero_WhenAddPolicyRule_ThenServiceAcceptsItAndDelegates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addPolicyRule(eventId,
                Optional.of(18f), Optional.of(1), Optional.of(0), Optional.of(true));

        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenNegativeAge_WhenAddPolicyRule_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(eventId,
                        Optional.of(-1f), Optional.empty(), Optional.empty(), Optional.empty()));
        assertTrue(ex.getMessage().toLowerCase().contains("age"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeMinTicket_WhenAddPolicyRule_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(eventId,
                        Optional.empty(), Optional.of(-1), Optional.empty(), Optional.empty()));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeMaxTicket_WhenAddPolicyRule_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(eventId,
                        Optional.empty(), Optional.empty(), Optional.of(-1), Optional.empty()));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddPolicyRule_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(eventId,
                        Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true)));
        assertEquals("Event not found", ex.getMessage());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAllFourFlagsTrueAndAllRulesPresent_WhenDeletePolicyRule_ThenAllRulesAreRemoved() {
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        assertEquals("precondition: 4 rules pre-populated",
                4, event.getPurchasePolicy().getRulesView().size());
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.deletePolicyRule(eventId, true, true, true, true);

        assertEquals(0, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAllFlagsFalse_WhenDeletePolicyRule_ThenNoRuleIsRemoved() {
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.deletePolicyRule(eventId, false, false, false, false);

        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenMixedFlags_WhenDeletePolicyRule_ThenExactlyTheSelectedRulesAreRemoved() {
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.deletePolicyRule(eventId, true, false, true, false);

        assertEquals(2, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenEventDoesNotExist_WhenDeletePolicyRule_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.deletePolicyRule(eventId, true, true, true, true));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenValidDatesAndDiscount_WhenAddOvertDiscount_ThenDiscountRuleIsAddedToEvent() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(7);

        eventService.addOvertDiscount(eventId, from, to, 10f);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenToDateInPast_WhenAddOvertDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 10f));
        assertTrue(ex.getMessage().toLowerCase().contains("todate"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddOvertDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 100.01f));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeDiscount_WhenAddOvertDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), -0.01f));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountIsZero_WhenAddOvertDiscount_ThenDiscountRuleIsStillAdded() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addOvertDiscount(eventId, LocalDate.now(), LocalDate.now().plusDays(7), 0f);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenDiscountIsHundred_WhenAddOvertDiscount_ThenDiscountRuleIsStillAdded() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addOvertDiscount(eventId, LocalDate.now(), LocalDate.now().plusDays(7), 100f);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenToDateIsToday_WhenAddOvertDiscount_ThenDiscountRuleIsAdded() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDate today = LocalDate.now();

        eventService.addOvertDiscount(eventId, today.minusDays(1), today, 10f);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddOvertDiscount_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenValidParameters_WhenAddConditionalDiscount_ThenDiscountRuleIsAddedToEvent() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addConditionalDiscount(eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenToDateInPast_WhenAddConditionalDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 10f, 3, 2));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddConditionalDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 150f, 3, 2));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeDiscount_WhenAddConditionalDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), -1f, 3, 2));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeRequiredTickets_WhenAddConditionalDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, -1, 2));
        assertTrue(ex.getMessage().toLowerCase().contains("required"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeAppliedTickets_WhenAddConditionalDiscount_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, -1));
        assertTrue(ex.getMessage().toLowerCase().contains("applied"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenZeroRequiredAndAppliedTickets_WhenAddConditionalDiscount_ThenDiscountRuleIsAdded() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addConditionalDiscount(eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 10f, 0, 0);

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddConditionalDiscount_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenValidParameters_WhenAddCouponCode_ThenCouponRuleIsAddedToEvent() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addCouponCode(eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25");

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenToDateInPast_WhenAddCouponCode_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 25f, "SUMMER25"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddCouponCode_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 200f, "SUMMER25"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeDiscount_WhenAddCouponCode_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), -1f, "SUMMER25"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddCouponCode_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25"));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenExistingDiscountId_WhenRemoveDiscount_ThenDiscountIsRemovedFromEvent() {
        Event event = newRealEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 10f);
        assertEquals("precondition: 1 discount pre-populated",
                1, event.getDiscountPolicy().gDiscountRules().size());
        IDiscountRule existing = event.getDiscountPolicy().gDiscountRules().get(0);
        UUID realDiscountId = existing.getId();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.removeDiscount(eventId, realDiscountId);

        assertEquals(0, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenUnknownDiscountId_WhenRemoveDiscount_ThenNoRuleIsRemoved() {
        Event event = newRealEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 10f);
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.removeDiscount(eventId, UUID.randomUUID());

        assertEquals(1, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenEventDoesNotExist_WhenRemoveDiscount_ThenDomainThrowsIllegalArgumentException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.removeDiscount(eventId, discountId));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenValidRating_WhenRateEvent_ThenEventRatingIsUpdatedAndEventIsSaved() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.rateEvent(userId, eventId, 4);

        assertEquals("event rating must reflect the single rating submitted",
                4.0, event.getRating(), 0.0001);
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenRatingZero_WhenRateEvent_ThenServiceAcceptsItAndDelegates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.rateEvent(userId, eventId, 0);

        assertEquals(0.0, event.getRating(), 0.0001);
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenRatingFive_WhenRateEvent_ThenServiceAcceptsItAndDelegates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.rateEvent(userId, eventId, 5);

        assertEquals(5.0, event.getRating(), 0.0001);
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenNegativeRating_WhenRateEvent_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.rateEvent(userId, eventId, -1));
        assertTrue(ex.getMessage().toLowerCase().contains("rating"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenRatingAboveFive_WhenRateEvent_ThenThrowsIllegalArgumentExceptionAtServiceLayer() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.rateEvent(userId, eventId, 6));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenUserHasAlreadyRatedTheEvent_WhenRateEvent_ThenDomainExceptionIsSwallowedBySerivce() {
        Event event = newRealEvent();
        event.addRating(userId, 3);
        when(eventRepository.getById(eventId)).thenReturn(event);

        try {
            eventService.rateEvent(userId, eventId, 5);
        } catch (Throwable t) {
            fail("expected no exception to escape the service, but got: " + t);
        }

        assertEquals("rating must remain the original value",
                3.0, event.getRating(), 0.0001);
        verify(eventRepository, never()).save(event);
    }

    @Test
    public void GivenEventDoesNotExist_WhenRateEvent_ThenDomainExceptionIsSwallowedBySerivce() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        try {
            eventService.rateEvent(userId, eventId, 4);
        } catch (Throwable t) {
            fail("expected no exception to escape the service, but got: " + t);
        }

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenSaveThrowsRuntimeException_WhenRateEvent_ThenExceptionPropagates() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        doThrow(new RuntimeException("DB down"))
                .when(eventRepository).save(event);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> eventService.rateEvent(userId, eventId, 4));
        assertEquals("DB down", ex.getMessage());
    }

    @Test
    public void GivenManyConcurrentValidRatingsFromDistinctUsers_WhenRateEvent_ThenAllAreAcceptedAndPersisted()
            throws InterruptedException {
        int threads = 16;
        int callsPerThread = 25;
        int expectedTotal = threads * callsPerThread;

        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger leakedFailures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < callsPerThread; j++) {
                        try {
                            eventService.rateEvent(UUID.randomUUID(), eventId, 4);
                        } catch (Throwable t) {
                            leakedFailures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean completed = done.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue("concurrent rating did not complete in time", completed);
        assertEquals("no caller-visible exception is expected for valid ratings",
                0, leakedFailures.get());
        verify(eventRepository, times(expectedTotal)).save(event);
        assertEquals("event rating must be exactly 4.0 since every rating is 4",
                4.0, event.getRating(), 0.0001);
    }

    @Test
    public void GivenMixedValidAndInvalidConcurrentRatings_WhenRateEvent_ThenInvariantsHold()
            throws InterruptedException {
        int threads = 32;
        int validCount = threads / 2;

        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger illegalCaught = new AtomicInteger();
        AtomicInteger unexpectedFailures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    int rating = (idx % 2 == 0) ? 3 : 99;
                    try {
                        eventService.rateEvent(UUID.randomUUID(), eventId, rating);
                    } catch (IllegalArgumentException expected) {
                        illegalCaught.incrementAndGet();
                    } catch (Throwable unexpected) {
                        unexpectedFailures.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue("mixed concurrent rating did not complete in time", completed);
        assertEquals("no exception other than IllegalArgumentException must escape the service",
                0, unexpectedFailures.get());
        assertEquals("every invalid rating must throw IllegalArgumentException",
                validCount, illegalCaught.get());
        verify(eventRepository, times(validCount)).save(event);
        assertNotEquals("event rating must reflect the valid submissions",
                0.0, event.getRating(), 0.0001);
    }

    @Test
    public void GivenManyConcurrentDiscountRegistrations_WhenAddOvertDiscount_ThenEveryRequestReachesTheRepository()
            throws InterruptedException {
        int threads = 20;
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger leakedFailures = new AtomicInteger();
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(7);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    eventService.addOvertDiscount(eventId, from, to, 15f);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    leakedFailures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue("concurrent discount registration did not complete in time", completed);
        assertEquals("the application service must not raise an exception of its own",
                0, leakedFailures.get());
        verify(eventRepository, times(threads)).getById(eventId);
        assertTrue("at least one discount rule must be present after the storm",
                event.getDiscountPolicy().gDiscountRules().size() >= 1);
        verify(eventRepository, atLeast(1)).getById(eventId);
    }

    @Test
    public void GivenValidArgs_WhenAddEvent_ThenEventIsSavedToRepository() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        eventService.addEvent(eventId, companyId, LocalDateTime.now().plusDays(10),
                "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE);

        verify(eventRepository).getById(eventId);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenNullEventId_WhenAddEvent_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(null, companyId, LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullCompanyId_WhenAddEvent_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(eventId, null, LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenEventAlreadyExists_WhenAddEvent_ThenThrowsDomainException() {
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        assertThrows(DomainException.class,
                () -> eventService.addEvent(eventId, companyId, LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenExistingEventAndAllFields_WhenEditEvent_ThenFieldsAreUpdatedAndReturnsTrue() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDateTime newDate = LocalDateTime.now().plusDays(60);

        boolean result = eventService.editEvent(eventId, newDate, "Haifa",
                "New Artist", "festival", EventStatus.CANCELED);

        assertTrue("editEvent must return true on success", result);
        assertEquals(newDate, event.getDate());
        assertEquals("Haifa", event.getLocation());
        assertEquals("New Artist", event.getArtist());
        assertEquals("festival", event.getType());
        assertEquals(EventStatus.CANCELED, event.getStatus());
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenAllNullFieldsExceptId_WhenEditEvent_ThenEventIsUnchangedAndReturnsTrue() {
        Event event = newRealEvent();
        String originalLocation = event.getLocation();
        when(eventRepository.getById(eventId)).thenReturn(event);

        boolean result = eventService.editEvent(eventId, null, null, null, null, null);

        assertTrue(result);
        assertEquals(originalLocation, event.getLocation());
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenNullEventId_WhenEditEvent_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.editEvent(null, null, null, null, null, null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenEventDoesNotExist_WhenEditEvent_ThenThrowsDomainException() {
        when(eventRepository.getById(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> eventService.editEvent(eventId, null, null, null, null, null));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenExistingEvent_WhenDeleteEvent_ThenRepositoryDeleteIsCalledAndReturnsTrue() {
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        boolean result = eventService.deleteEvent(eventId);

        assertTrue(result);
        verify(eventRepository).delete(eventId);
    }

    @Test
    public void GivenNullEventId_WhenDeleteEvent_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.deleteEvent(null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenEventDoesNotExist_WhenDeleteEvent_ThenThrowsDomainException() {
        when(eventRepository.getById(eventId)).thenReturn(null);
        assertThrows(DomainException.class, () -> eventService.deleteEvent(eventId));
        verify(eventRepository, never()).delete(any(UUID.class));
    }

    @Test
    public void GivenStandingAreaAndPositiveCount_WhenAddStandingTickets_ThenTicketsAreAddedToEvent() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addStandingTickets(eventId, areaId, 10);

        assertEquals(10, event.getTotalCapacity());
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenZeroCount_WhenAddStandingTickets_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(eventId, UUID.randomUUID(), 0));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenAddStandingTickets_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(null, UUID.randomUUID(), 5));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenAreaIsSittingArea_WhenAddStandingTickets_ThenThrowsIllegalArgumentException() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(eventId, areaId, 3));
    }

    @Test
    public void GivenSittingAreaAndPositiveDimensions_WhenAddSittingTickets_ThenAllSeatsAreCreated() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addSittingTickets(eventId, areaId, 3, 4);

        assertEquals("expected rows*seatsPerRow tickets", 12, event.getTotalCapacity());
        verify(eventRepository).save(event);
    }

    @Test
    public void GivenZeroRows_WhenAddSittingTickets_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addSittingTickets(eventId, UUID.randomUUID(), 0, 4));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenAreaIsStandingArea_WhenAddSittingTickets_ThenThrowsIllegalArgumentException() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.addSittingTickets(eventId, areaId, 2, 2));
    }

    @Test
    public void GivenFreshEvent_WhenGetTotalCapacity_ThenReturnsZero() {
        Event event = newRealEvent();
        assertEquals(0, event.getTotalCapacity());
    }

    @Test
    public void GivenStandingAndSittingTicketsAdded_WhenGetTotalCapacity_ThenReturnsSumOfTickets() {
        Event event = newRealEvent();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(standingAreaId, 50.0));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 100.0));

        event.addStandingTickets(standingAreaId, 5);
        event.addSittingTickets(sittingAreaId, 2, 3);

        assertEquals(11, event.getTotalCapacity());
    }

    @Test
    public void GivenOneActiveCompanyWithVisibleEvents_WhenBrowseCatalog_ThenCompanyAndEventsAreReturned() {
        Company c = newActiveCompany("Acme");
        Event e1 = newEventFor(c.getId(), "Show A", "Artist1", "concert",
                LocalDateTime.now().plusDays(10), "Tel Aviv", EventStatus.ACTIVE);
        Event e2 = newEventFor(c.getId(), "Show B", "Artist2", "festival",
                LocalDateTime.now().plusDays(20), "Haifa", EventStatus.ACTIVE);
        when(companyRepository.getAllActive()).thenReturn(Collections.singletonList(c));
        when(eventRepository.getAll()).thenReturn(Arrays.asList(e1, e2));

        List<CompanyCatalogDto> catalog = eventService.browseCatalog();

        assertEquals(1, catalog.size());
        CompanyCatalogDto row = catalog.get(0);
        assertEquals(c.getId(), row.companyId());
        assertEquals("Acme", row.companyName());
        assertEquals(2, row.events().size());
    }

    @Test
    public void GivenNoActiveCompanies_WhenBrowseCatalog_ThenReturnsEmptyListAndDoesNotThrow() {
        when(companyRepository.getAllActive()).thenReturn(Collections.emptyList());

        List<CompanyCatalogDto> catalog = eventService.browseCatalog();

        assertTrue("alt flow: empty catalog must be returned, not an exception", catalog.isEmpty());
    }

    @Test
    public void GivenCompanyHasMixedStatusEvents_WhenBrowseCatalog_ThenOnlyActiveEventsAreIncluded() {
        Company c = newActiveCompany("Acme");
        Event active = newEventFor(c.getId(), "A", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.ACTIVE);
        Event canceled = newEventFor(c.getId(), "B", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.CANCELED);
        Event ended = newEventFor(c.getId(), "C", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.ENDED);
        when(companyRepository.getAllActive()).thenReturn(Collections.singletonList(c));
        when(eventRepository.getAll()).thenReturn(Arrays.asList(active, canceled, ended));

        List<CompanyCatalogDto> catalog = eventService.browseCatalog();

        assertEquals(1, catalog.size());
        assertEquals("only ACTIVE events should appear in the catalog",
                1, catalog.get(0).events().size());
        assertEquals(active.getEventId(), catalog.get(0).events().get(0).eventId());
    }

    @Test
    public void GivenExistingEventWithAreasAndPolicies_WhenGetEventDetails_ThenAllSectionsArePopulated() {
        Event event = newRealEvent();
        event.setName("Headline Show");
        addStandingArea(event, 100.0);
        addSittingArea(event, 250.0);
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty());
        event.addOvertDiscount(LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), 10f);
        when(eventRepository.getById(eventId)).thenReturn(event);

        EventDetailsDto details = eventService.getEventDetails(eventId);

        assertNotNull(details);
        assertEquals(eventId, details.eventId());
        assertEquals(companyId, details.companyId());
        assertEquals("Headline Show", details.name());
        assertEquals(2, details.areas().size());
        List<String> kinds = new ArrayList<>();
        for (AreaSummaryDto a : details.areas()) {
            kinds.add(a.kind());
        }
        assertTrue(kinds.contains("STANDING"));
        assertTrue(kinds.contains("SITTING"));
        assertEquals(1, details.purchaseRuleNames().size());
        assertEquals(1, details.discountRuleNames().size());
    }

    @Test
    public void GivenUnknownEventId_WhenGetEventDetails_ThenThrowsDomainException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () -> eventService.getEventDetails(eventId));
    }

    @Test
    public void GivenNullEventId_WhenGetEventDetails_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> eventService.getEventDetails(null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenAllEventsBelongToActiveCompanies_WhenSearchEventsWithEmptyCriteria_ThenAllVisibleEventsReturned() {
        Company c = newActiveCompany("Acme");
        Event e1 = newEventFor(c.getId(), "X", "A1", "t1",
                LocalDateTime.now().plusDays(3), "Tel Aviv", EventStatus.ACTIVE);
        Event e2 = newEventFor(c.getId(), "Y", "A2", "t2",
                LocalDateTime.now().plusDays(4), "Haifa", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(e1, e2));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(EventSearchCriteria.empty());

        assertEquals(2, results.size());
    }

    @Test
    public void GivenEventsBelongToInactiveCompany_WhenSearchEvents_ThenThoseEventsAreExcluded() {
        Company active = newActiveCompany("Active");
        Company inactive = newActiveCompany("Closed");
        inactive.AdminClose();
        Event visibleA = newEventFor(active.getId(), "A", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        Event hiddenB = newEventFor(inactive.getId(), "B", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(visibleA, hiddenB));
        when(companyRepository.findByID(active.getId())).thenReturn(Optional.of(active));
        when(companyRepository.findByID(inactive.getId())).thenReturn(Optional.of(inactive));

        List<EventSummaryDto> results = eventService.searchEvents(EventSearchCriteria.empty());

        assertEquals(1, results.size());
        assertEquals(visibleA.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenSomeEventsAreCanceled_WhenSearchEvents_ThenOnlyActiveEventsReturned() {
        Company c = newActiveCompany("Acme");
        Event active = newEventFor(c.getId(), "A", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        Event canceled = newEventFor(c.getId(), "B", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.CANCELED);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(active, canceled));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(EventSearchCriteria.empty());

        assertEquals(1, results.size());
        assertEquals(active.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenTextCriteria_WhenSearchEvents_ThenReturnsEventsWhereTextMatchesNameArtistTypeOrTag() {
        Company c = newActiveCompany("Acme");
        Event byName = newEventFor(c.getId(), "Coldplay Live", "X", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        Event byArtist = newEventFor(c.getId(), "Show", "Coldplay", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        Event byType = newEventFor(c.getId(), "Show", "Other", "Coldplay-tribute",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        Event byTag = newEventFor(c.getId(), "Show", "Other", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE, "coldplay");
        Event noMatch = newEventFor(c.getId(), "Show", "Other", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(byName, byArtist, byType, byTag, noMatch));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withText("coldplay"));

        assertEquals("name/artist/type/tag should all be searched", 4, results.size());
    }

    @Test
    public void GivenLocationCriteria_WhenSearchEvents_ThenReturnsEventsWhoseLocationContainsItCaseInsensitive() {
        Company c = newActiveCompany("Acme");
        Event tlv = newEventFor(c.getId(), "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Tel Aviv Convention Center", EventStatus.ACTIVE);
        Event hfa = newEventFor(c.getId(), "Y", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Haifa Park", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(tlv, hfa));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withLocation("tel aviv"));

        assertEquals(1, results.size());
        assertEquals(tlv.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenPriceRange_WhenSearchEvents_ThenReturnsEventsWithAtLeastOneAreaInRange() {
        Company c = newActiveCompany("Acme");
        Event cheap = newEventFor(c.getId(), "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        addStandingArea(cheap, 50.0);
        Event mixed = newEventFor(c.getId(), "Y", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        addStandingArea(mixed, 50.0);
        addSittingArea(mixed, 500.0);
        Event expensive = newEventFor(c.getId(), "Z", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        addStandingArea(expensive, 1000.0);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(cheap, mixed, expensive));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withPriceRange(100.0, 800.0));

        assertEquals("only events with an area inside [100, 800] should match", 1, results.size());
        assertEquals(mixed.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenDateRange_WhenSearchEvents_ThenReturnsEventsInRange() {
        Company c = newActiveCompany("Acme");
        LocalDateTime now = LocalDateTime.now();
        Event tooEarly = newEventFor(c.getId(), "E", "Ar", "t",
                now.plusDays(1), "Loc", EventStatus.ACTIVE);
        Event inRange = newEventFor(c.getId(), "I", "Ar", "t",
                now.plusDays(10), "Loc", EventStatus.ACTIVE);
        Event tooLate = newEventFor(c.getId(), "L", "Ar", "t",
                now.plusDays(60), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(tooEarly, inRange, tooLate));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withDateRange(now.plusDays(5), now.plusDays(30)));

        assertEquals(1, results.size());
        assertEquals(inRange.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenMinEventRating_WhenSearchEvents_ThenLowRatedEventsAreExcluded() {
        Company c = newActiveCompany("Acme");
        Event low = newEventFor(c.getId(), "L", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event high = newEventFor(c.getId(), "H", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        low.addRating(UUID.randomUUID(), 2);
        high.addRating(UUID.randomUUID(), 5);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(low, high));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withMinEventRating(4.0));

        assertEquals(1, results.size());
        assertEquals(high.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenMinCompanyRating_WhenSearchEvents_ThenEventsFromLowRatedCompaniesAreExcluded() {
        Company low = newActiveCompany("Low");
        Company high = newActiveCompany("High");
        low.addRating(UUID.randomUUID(), 2);
        high.addRating(UUID.randomUUID(), 5);
        Event eLow = newEventFor(low.getId(), "L", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event eHigh = newEventFor(high.getId(), "H", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(eLow, eHigh));
        when(companyRepository.findByID(low.getId())).thenReturn(Optional.of(low));
        when(companyRepository.findByID(high.getId())).thenReturn(Optional.of(high));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withMinCompanyRating(4.0));

        assertEquals(1, results.size());
        assertEquals(eHigh.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenNoEventMatchesCriteria_WhenSearchEvents_ThenReturnsEmptyListAndDoesNotThrow() {
        Company c = newActiveCompany("Acme");
        Event e = newEventFor(c.getId(), "Concert", "Artist", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Collections.singletonList(e));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withText("nonexistent-keyword"));

        assertTrue("alt flow: empty list must be returned, not an exception", results.isEmpty());
    }

    @Test
    public void GivenNullCriteria_WhenSearchEvents_ThenTreatedAsEmptyCriteria() {
        Company c = newActiveCompany("Acme");
        Event e = newEventFor(c.getId(), "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Collections.singletonList(e));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(null);

        assertEquals(1, results.size());
    }

    @Test
    public void GivenTwoCompaniesEachWithEvents_WhenSearchEventsByCompany_ThenOnlyTargetCompanysEventsReturned() {
        Company a = newActiveCompany("A");
        Company b = newActiveCompany("B");
        Event a1 = newEventFor(a.getId(), "A1", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event a2 = newEventFor(a.getId(), "A2", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        Event b1 = newEventFor(b.getId(), "B1", "Ar", "t",
                LocalDateTime.now().plusDays(3), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(a1, a2, b1));
        when(companyRepository.findByID(a.getId())).thenReturn(Optional.of(a));
        when(companyRepository.findByID(b.getId())).thenReturn(Optional.of(b));

        List<EventSummaryDto> results = eventService.searchEventsByCompany(
                a.getId(), EventSearchCriteria.empty());

        assertEquals(2, results.size());
        for (EventSummaryDto r : results) {
            assertEquals("every result must belong to the requested company",
                    a.getId(), r.companyId());
        }
    }

    @Test
    public void GivenCriteriaWithText_WhenSearchEventsByCompany_ThenScopedAndTextFilterBothApply() {
        Company a = newActiveCompany("A");
        Company b = newActiveCompany("B");
        Event aMatch = newEventFor(a.getId(), "Coldplay", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event aOther = newEventFor(a.getId(), "Other", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event bMatch = newEventFor(b.getId(), "Coldplay", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(aMatch, aOther, bMatch));
        when(companyRepository.findByID(a.getId())).thenReturn(Optional.of(a));
        when(companyRepository.findByID(b.getId())).thenReturn(Optional.of(b));

        List<EventSummaryDto> results = eventService.searchEventsByCompany(
                a.getId(), EventSearchCriteria.empty().withText("coldplay"));

        assertEquals(1, results.size());
        assertEquals(aMatch.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenUnknownCompanyId_WhenSearchEventsByCompany_ThenReturnsEmptyListWithoutThrowing() {
        UUID unknown = UUID.randomUUID();
        when(eventRepository.getAll()).thenReturn(Collections.emptyList());

        List<EventSummaryDto> results = eventService.searchEventsByCompany(
                unknown, EventSearchCriteria.empty());

        assertTrue(results.isEmpty());
    }

    @Test
    public void GivenNullCompanyId_WhenSearchEventsByCompany_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEventsByCompany(null, EventSearchCriteria.empty()));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenPriceMinGreaterThanPriceMax_WhenSearchEvents_ThenThrowsIllegalArgumentException() {
        EventSearchCriteria bad = EventSearchCriteria.empty().withPriceRange(200.0, 100.0);
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativePriceMin_WhenSearchEvents_ThenThrowsIllegalArgumentException() {
        EventSearchCriteria bad = EventSearchCriteria.empty().withPriceRange(-1.0, 100.0);
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
    }

    @Test
    public void GivenDateFromAfterDateTo_WhenSearchEvents_ThenThrowsIllegalArgumentException() {
        LocalDateTime now = LocalDateTime.now();
        EventSearchCriteria bad = EventSearchCriteria.empty()
                .withDateRange(now.plusDays(10), now.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
    }

    @Test
    public void GivenMinEventRatingOutOfRange_WhenSearchEvents_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinEventRating(-1.0)));
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinEventRating(6.0)));
    }

    @Test
    public void GivenMinCompanyRatingOutOfRange_WhenSearchEvents_ThenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinCompanyRating(-0.5)));
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinCompanyRating(5.5)));
    }

    @Test
    public void GivenNonActiveEvent_WhenMatches_ThenReturnsFalseRegardlessOfCriteria() {
        Event canceled = newEventFor(companyId, "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.CANCELED);
        assertFalse("non-ACTIVE event must never match",
                canceled.matches(EventSearchCriteria.empty(), 5.0));
    }

    @Test
    public void GivenCompanyIdCriteria_WhenMatches_ThenOnlyEventsFromThatCompanyPass() {
        UUID otherCompany = UUID.randomUUID();
        Event mine = newEventFor(companyId, "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event theirs = newEventFor(otherCompany, "Y", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        EventSearchCriteria scoped = EventSearchCriteria.empty().withCompanyId(companyId);
        assertTrue(mine.matches(scoped, 0.0));
        assertFalse(theirs.matches(scoped, 0.0));
    }

    @Test(timeout = 15000)
    public void GivenWritersAddingEventsAndReadersSearching_WhenRunConcurrently_ThenNoExceptionsAndFinalCountMatchesTotalWrites() throws Exception {
        final Company company = newActiveCompany("Acme");
        final CopyOnWriteArrayList<Event> store = new CopyOnWriteArrayList<>();
        when(companyRepository.findByID(company.getId())).thenReturn(Optional.of(company));
        when(eventRepository.getAll()).thenAnswer(inv -> new ArrayList<>(store));

        final int writerCount = 4;
        final int readerCount = 4;
        final int eventsPerWriter = 25;
        final int readsPerReader = 50;
        final int totalThreads = writerCount + readerCount;

        ExecutorService exec = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);
        AtomicInteger errors = new AtomicInteger(0);

        try {
            for (int w = 0; w < writerCount; w++) {
                exec.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < eventsPerWriter; i++) {
                            Event e = new Event(UUID.randomUUID(), company.getId(),
                                    LocalDateTime.now().plusDays(1),
                                    "Tel Aviv", "Artist", "concert", EventStatus.ACTIVE);
                            store.add(e);
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            for (int r = 0; r < readerCount; r++) {
                exec.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < readsPerReader; i++) {
                            List<EventSummaryDto> snapshot = eventService.searchEvents(
                                    EventSearchCriteria.empty());
                            for (EventSummaryDto s : snapshot) {
                                assertEquals("every visible event must belong to the active company",
                                        company.getId(), s.companyId());
                            }
                        }
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue("threads must finish in time",
                    done.await(10, TimeUnit.SECONDS));
        } finally {
            exec.shutdownNow();
        }

        assertEquals("no exceptions should be thrown by readers or writers", 0, errors.get());

        List<EventSummaryDto> finalSnapshot =
                eventService.searchEvents(EventSearchCriteria.empty());
        assertEquals("after settling, all writes should be visible",
                writerCount * eventsPerWriter, finalSnapshot.size());
    }
}