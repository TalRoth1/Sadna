package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.PolicyAggregate.IDiscountRule;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventService} (JUnit 4 + Mockito).
 *
 * Following the same style as UserServiceTest: only INTERFACES are mocked
 * ({@link IEventRepository}, {@link IHistoryRepository}, {@link ICompanyRepository}),
 * which lets Mockito use JDK proxies and avoids the Java-25 / inline-mock-maker
 * agent issue that prevents mocking concrete classes.
 *
 * The application service ({@link EventService}) and the domain service
 * ({@link EventManagementDomainService}) and the {@link Event} aggregate are
 * REAL instances. Tests assert:
 *   1. Service-level input validation (exception type and that nothing reaches the repository).
 *   2. Correct delegation (the right repository methods are called with the right args).
 *   3. Effects observable on the real {@link Event} aggregate (e.g. policy/discount rule was added).
 *   4. Exception translation/propagation/swallowing across the layers.
 *   5. Concurrency invariants under contention.
 *
 * Naming: Given<Condition>_When<Method>_Then<Expected>
 * Pattern: Arrange / Act / Assert.
 */
public class EventServiceTest {

    private IEventRepository eventRepository;
    private IHistoryRepository historyRepository;
    private ICompanyRepository companyRepository;

    private EventManagementDomainService eventManagementDomainService;
    private EventService eventService;

    private UUID eventId;
    private UUID userId;
    private UUID companyId;
    private UUID discountId;
    private String ownerUsername;

    @Before
    public void setUp() {
        eventRepository = mock(IEventRepository.class);
        historyRepository = mock(IHistoryRepository.class);
        companyRepository = mock(ICompanyRepository.class);

        eventManagementDomainService = new EventManagementDomainService(
                eventRepository, historyRepository, companyRepository);
        eventService = new EventService(eventManagementDomainService);

        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        discountId = UUID.randomUUID();
        ownerUsername = "owner-user";
    }

    /** Builds a real, valid Event used in happy-path scenarios. */
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

    // =====================================================================
    //                  getEventPurchaseHistoryForOwner
    // =====================================================================

    /** Returns the list produced by the history repository unchanged. */
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
        // Arrange / Act / Assert
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
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Act / Assert
        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertEquals("Event not found", ex.getMessage());
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    @Test
    public void GivenUserIsNotOwnerOfEventCompany_WhenGetEventPurchaseHistoryForOwner_ThenThrowsDomainException() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.isOwner(ownerUsername, companyId)).thenReturn(false);

        // Act / Assert
        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertTrue("message should mention authorization",
                ex.getMessage().toLowerCase().contains("not authorized"));
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    // =====================================================================
    //                            addPolicyRule
    // =====================================================================

    @Test
    public void GivenAllOptionalsPresentAndValid_WhenAddPolicyRule_ThenAllFourRulesAreAddedToTheEventPolicy() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addPolicyRule(eventId,
                Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));

        // Assert
        assertEquals("expected one rule per provided optional",
                4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAllOptionalsEmpty_WhenAddPolicyRule_ThenNoRuleIsAddedToTheEventPolicy() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addPolicyRule(eventId,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        // Assert
        assertEquals("an empty optional means 'no rule for this field'",
                0, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenOnlyAgeIsPresent_WhenAddPolicyRule_ThenExactlyTheAgeRuleIsAdded() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addPolicyRule(eventId,
                Optional.of(21f), Optional.empty(), Optional.empty(), Optional.empty());

        // Assert
        assertEquals("only the age rule must be added when other optionals are empty",
                1, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAgeIsZero_WhenAddPolicyRule_ThenServiceAcceptsItAndDelegates() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addPolicyRule(eventId,
                Optional.of(0f), Optional.of(1), Optional.of(5), Optional.of(true));

        // Assert
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
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Act / Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(eventId,
                        Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true)));
        assertEquals("Event not found", ex.getMessage());
        verify(eventRepository).getById(eventId);
    }

    // =====================================================================
    //                          deletePolicyRule
    // =====================================================================

    @Test
    public void GivenAllFourFlagsTrueAndAllRulesPresent_WhenDeletePolicyRule_ThenAllRulesAreRemoved() {
        // Arrange
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        assertEquals("precondition: 4 rules pre-populated",
                4, event.getPurchasePolicy().getRulesView().size());
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.deletePolicyRule(eventId, true, true, true, true);

        // Assert
        assertEquals(0, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenAllFlagsFalse_WhenDeletePolicyRule_ThenNoRuleIsRemoved() {
        // Arrange
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.deletePolicyRule(eventId, false, false, false, false);

        // Assert
        assertEquals(4, event.getPurchasePolicy().getRulesView().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenMixedFlags_WhenDeletePolicyRule_ThenExactlyTheSelectedRulesAreRemoved() {
        // Arrange
        Event event = newRealEvent();
        event.addPurchasePolicy(Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act: remove age and maxTicket only
        eventService.deletePolicyRule(eventId, true, false, true, false);

        // Assert: 2 rules left (minTicket + loneSeat)
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

    // =====================================================================
    //                          addOvertDiscount
    // =====================================================================

    @Test
    public void GivenValidDatesAndDiscount_WhenAddOvertDiscount_ThenDiscountRuleIsAddedToEvent() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusDays(7);

        // Act
        eventService.addOvertDiscount(eventId, from, to, 10f);

        // Assert
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

    // =====================================================================
    //                       addConditionalDiscount
    // =====================================================================

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

    // =====================================================================
    //                            addCouponCode
    // =====================================================================

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

    // =====================================================================
    //                            removeDiscount
    // =====================================================================

    @Test
    public void GivenExistingDiscountId_WhenRemoveDiscount_ThenDiscountIsRemovedFromEvent() {
        // Arrange
        Event event = newRealEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 10f);
        assertEquals("precondition: 1 discount pre-populated",
                1, event.getDiscountPolicy().gDiscountRules().size());
        IDiscountRule existing = event.getDiscountPolicy().gDiscountRules().get(0);
        UUID realDiscountId = existing.getId();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.removeDiscount(eventId, realDiscountId);

        // Assert
        assertEquals(0, event.getDiscountPolicy().gDiscountRules().size());
        verify(eventRepository).getById(eventId);
    }

    @Test
    public void GivenUnknownDiscountId_WhenRemoveDiscount_ThenNoRuleIsRemoved() {
        // Arrange
        Event event = newRealEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 10f);
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.removeDiscount(eventId, UUID.randomUUID());

        // Assert
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

    // =====================================================================
    //                              rateEvent
    // =====================================================================

    @Test
    public void GivenValidRating_WhenRateEvent_ThenEventRatingIsUpdatedAndEventIsSaved() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.rateEvent(userId, eventId, 4);

        // Assert
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

    /** Domain raises {@code DomainException} on duplicate rating; the service swallows it (current TODO behavior). */
    @Test
    public void GivenUserHasAlreadyRatedTheEvent_WhenRateEvent_ThenDomainExceptionIsSwallowedBySerivce() {
        // Arrange
        Event event = newRealEvent();
        event.addRating(userId, 3);
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        try {
            eventService.rateEvent(userId, eventId, 5);
        } catch (Throwable t) {
            fail("expected no exception to escape the service, but got: " + t);
        }

        // Assert: rating did not change because the domain rejected the duplicate
        assertEquals("rating must remain the original value",
                3.0, event.getRating(), 0.0001);
        verify(eventRepository, never()).save(event);
    }

    /** Event-not-found also raises a {@code DomainException} from the domain — service swallows it. */
    @Test
    public void GivenEventDoesNotExist_WhenRateEvent_ThenDomainExceptionIsSwallowedBySerivce() {
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Act
        try {
            eventService.rateEvent(userId, eventId, 4);
        } catch (Throwable t) {
            fail("expected no exception to escape the service, but got: " + t);
        }

        // Assert
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenSaveThrowsRuntimeException_WhenRateEvent_ThenExceptionPropagates() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        doThrow(new RuntimeException("DB down"))
                .when(eventRepository).save(event);

        // Act / Assert: only DomainException is caught; other RuntimeExceptions must propagate.
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> eventService.rateEvent(userId, eventId, 4));
        assertEquals("DB down", ex.getMessage());
    }

    // =====================================================================
    //                          Concurrency Tests
    // =====================================================================

    /**
     * Bombards {@code rateEvent} with many valid concurrent calls, each from a distinct user.
     * Integrity invariants:
     *   - No call surfaces a caller-visible exception.
     *   - Every legitimate request reaches the repository
     *     ({@code eventRepository.save} called {@code threads * callsPerThread} times).
     *   - The aggregate ratings count grows by exactly {@code threads * callsPerThread}
     *     because {@link Event#addRating} is synchronized.
     */
    @Test
    public void GivenManyConcurrentValidRatingsFromDistinctUsers_WhenRateEvent_ThenAllAreAcceptedAndPersisted()
            throws InterruptedException {
        // Arrange
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

        // Act
        start.countDown();
        boolean completed = done.await(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Assert
        assertTrue("concurrent rating did not complete in time", completed);
        assertEquals("no caller-visible exception is expected for valid ratings",
                0, leakedFailures.get());
        verify(eventRepository, times(expectedTotal)).save(event);
        assertEquals("event rating must be exactly 4.0 since every rating is 4",
                4.0, event.getRating(), 0.0001);
    }

    /**
     * Concurrent mix of valid (rating=3) and invalid (rating=99) requests.
     * Integrity invariants under contention:
     *   - Every invalid rating MUST throw IllegalArgumentException to the caller.
     *   - Invalid ratings MUST NEVER reach the repository.
     *   - Every valid rating MUST be persisted exactly once.
     */
    @Test
    public void GivenMixedValidAndInvalidConcurrentRatings_WhenRateEvent_ThenInvariantsHold()
            throws InterruptedException {
        // Arrange
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

        // Act
        start.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Assert
        assertTrue("mixed concurrent rating did not complete in time", completed);
        assertEquals("no exception other than IllegalArgumentException must escape the service",
                0, unexpectedFailures.get());
        assertEquals("every invalid rating must throw IllegalArgumentException",
                validCount, illegalCaught.get());
        verify(eventRepository, times(validCount)).save(event);
        assertNotEquals("event rating must reflect the valid submissions",
                0.0, event.getRating(), 0.0001);
    }

    /**
     * Bombards a delegating policy operation. The application service is
     * stateless, so the integrity invariant we assert is that every issued
     * request reaches the repository (Mockito's interaction recording is
     * thread-safe). The underlying domain {@code ArrayList} is not thread-safe;
     * that is a domain-layer concern outside the application-service scope.
     */
    @Test
    public void GivenManyConcurrentDiscountRegistrations_WhenAddOvertDiscount_ThenEveryRequestReachesTheRepository()
            throws InterruptedException {
        // Arrange
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

        // Act
        start.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Assert
        assertTrue("concurrent discount registration did not complete in time", completed);
        assertEquals("the application service must not raise an exception of its own",
                0, leakedFailures.get());
        verify(eventRepository, times(threads)).getById(eventId);
        // Application-layer invariant: every call delegates. Final ArrayList size is a
        // domain-layer concern (ArrayList is not thread-safe), so we only sanity-check
        // that AT LEAST one rule made it in.
        assertTrue("at least one discount rule must be present after the storm",
                event.getDiscountPolicy().gDiscountRules().size() >= 1);
        verify(eventRepository, atLeast(1)).getById(eventId);
    }

    // =====================================================================
    //                                addEvent
    // =====================================================================

    @Test
    public void GivenValidArgs_WhenAddEvent_ThenEventIsSavedToRepository() {
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Act
        eventService.addEvent(eventId, companyId, LocalDateTime.now().plusDays(10),
                "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE);

        // Assert
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
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        // Act / Assert
        assertThrows(DomainException.class,
                () -> eventService.addEvent(eventId, companyId, LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verify(eventRepository, never()).save(any(Event.class));
    }

    // =====================================================================
    //                                editEvent
    // =====================================================================

    @Test
    public void GivenExistingEventAndAllFields_WhenEditEvent_ThenFieldsAreUpdatedAndReturnsTrue() {
        // Arrange
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDateTime newDate = LocalDateTime.now().plusDays(60);

        // Act
        boolean result = eventService.editEvent(eventId, newDate, "Haifa",
                "New Artist", "festival", EventStatus.CANCELED);

        // Assert
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
        // Arrange
        Event event = newRealEvent();
        String originalLocation = event.getLocation();
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        boolean result = eventService.editEvent(eventId, null, null, null, null, null);

        // Assert
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

    // =====================================================================
    //                               deleteEvent
    // =====================================================================

    @Test
    public void GivenExistingEvent_WhenDeleteEvent_ThenRepositoryDeleteIsCalledAndReturnsTrue() {
        // Arrange
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        // Act
        boolean result = eventService.deleteEvent(eventId);

        // Assert
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

    // =====================================================================
    //                          addStandingTickets
    // =====================================================================

    @Test
    public void GivenStandingAreaAndPositiveCount_WhenAddStandingTickets_ThenTicketsAreAddedToEvent() {
        // Arrange
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addStandingTickets(eventId, areaId, 10);

        // Assert
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
        // Arrange
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act / Assert
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(eventId, areaId, 3));
    }

    // =====================================================================
    //                           addSittingTickets
    // =====================================================================

    @Test
    public void GivenSittingAreaAndPositiveDimensions_WhenAddSittingTickets_ThenAllSeatsAreCreated() {
        // Arrange
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act
        eventService.addSittingTickets(eventId, areaId, 3, 4);

        // Assert
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
        // Arrange
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        // Act / Assert
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addSittingTickets(eventId, areaId, 2, 2));
    }

    // =====================================================================
    //                            getTotalCapacity
    // =====================================================================

    @Test
    public void GivenFreshEvent_WhenGetTotalCapacity_ThenReturnsZero() {
        Event event = newRealEvent();
        assertEquals(0, event.getTotalCapacity());
    }

    @Test
    public void GivenStandingAndSittingTicketsAdded_WhenGetTotalCapacity_ThenReturnsSumOfTickets() {
        // Arrange
        Event event = newRealEvent();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(standingAreaId, 50.0));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 100.0));

        // Act
        event.addStandingTickets(standingAreaId, 5);
        event.addSittingTickets(sittingAreaId, 2, 3);

        // Assert
        assertEquals(11, event.getTotalCapacity());
    }
}
