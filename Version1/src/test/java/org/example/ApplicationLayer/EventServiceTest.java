package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.AreaSummaryDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventSummaryDto;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

    @Mock
    private IEventRepository eventRepository;
    @Mock
    private ICompanyRepository companyRepository;
    @Mock
    private IHistoryRepository historyRepository;
    @Mock
    private IUserRepository userRepository;

    private EventService eventService;

    private UUID eventId;
    private UUID userId;
    private UUID companyId;
    private UUID discountId;
    private String ownerUsername;

    private INotifier notifier;

    @Before
    public void setUp() {

        notifier = mock(INotifier.class);
        EventManagementDomainService eventManagementDomainService =
                new EventManagementDomainService(eventRepository, historyRepository, companyRepository, userRepository);
        eventService = new EventService(eventManagementDomainService, notifier);

        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        discountId = UUID.randomUUID();
        ownerUsername = "owner-user";
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Event newRealEvent() {
        return new Event(eventId, companyId, LocalDateTime.now().plusDays(30),
                "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE);
    }

    private Company newActiveCompany(String name) {
        return new Company("founder-" + name, name);
    }

    private Company authorizedCompany(String founderUsername, UUID eventId) {
        Company c = new Company(founderUsername, "TestCompany");
        c.addEvent(eventId);
        return c;
    }

    private Event eventOf(UUID compId, String name, String artist, String type,
                          LocalDateTime date, String location,
                          EventStatus status, String... tags) {
        Event e = new Event(UUID.randomUUID(), compId, date, location, artist, type, status);
        e.setName(name);
        for (String t : tags) {
            e.addTag(t);
        }
        return e;
    }

    private void stubAuthorizedRepositories(Event event) {
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId))
                .thenReturn(Optional.of(authorizedCompany(ownerUsername, eventId)));
        
        org.example.DomainLayer.UserAggregate.User ownerUser =
            new org.example.DomainLayer.UserAggregate.User(UUID.randomUUID(), ownerUsername, ownerUsername, "hash", 40);
        ownerUser.getCompanyRoles().put(companyId, new org.example.DomainLayer.UserAggregate.CompanyFounder(ownerUsername));
        ownerUser.getCompanyRole(companyId).getEventsIds().add(event.getEventId());
        
        when(userRepository.findByEmail(ownerUsername)).thenReturn(Optional.of(ownerUser));
        when(userRepository.hasPermission(ownerUsername, companyId,
            org.example.DomainLayer.CompanyAggregate.CompanyPermission.MANAGE_POLICIES, eventId))
            .thenReturn(true);
    }

    // =====================================================================
    // Service-Layer Input Validation
    // =====================================================================

    @Test
    public void GivenNullOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(null, eventId));
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(historyRepository);
        verifyNoInteractions(companyRepository);
    }

    @Test
    public void GivenEmptyOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner("", eventId));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenBlankOwnerUsername_WhenGetEventPurchaseHistoryForOwner_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventPurchaseHistoryForOwner("   ", eventId));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeAge_WhenAddPolicyRule_ThenIllegalArgumentExceptionIsThrown() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(ownerUsername, companyId, eventId,
                        Optional.of(-1f), Optional.empty(), Optional.empty(), Optional.empty(), true));
        assertTrue(ex.getMessage().toLowerCase().contains("age"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeMinTicket_WhenAddPolicyRule_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(ownerUsername, companyId, eventId,
                        Optional.empty(), Optional.of(-1), Optional.empty(), Optional.empty(), true));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeMaxTicket_WhenAddPolicyRule_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(ownerUsername, companyId, eventId,
                        Optional.empty(), Optional.empty(), Optional.of(-1), Optional.empty(), true));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenToDateInPast_WhenAddOvertDiscount_ThenIllegalArgumentExceptionIsThrown() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 10f));
        assertTrue(ex.getMessage().toLowerCase().contains("todate"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddOvertDiscount_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 100.01f));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeDiscount_WhenAddOvertDiscount_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), -0.01f));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenToDateInPast_WhenAddConditionalDiscount_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 10f, 3, 2));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddConditionalDiscount_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 150f, 3, 2));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeRequiredTickets_WhenAddConditionalDiscount_ThenIllegalArgumentExceptionIsThrown() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, -1, 2));
        assertTrue(ex.getMessage().toLowerCase().contains("required"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeAppliedTickets_WhenAddConditionalDiscount_ThenIllegalArgumentExceptionIsThrown() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, -1));
        assertTrue(ex.getMessage().toLowerCase().contains("applied"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenToDateInPast_WhenAddCouponCode_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(ownerUsername, companyId, eventId,
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 25f, "SUMMER25"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDiscountAboveHundred_WhenAddCouponCode_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 200f, "SUMMER25"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativeRating_WhenRateEvent_ThenIllegalArgumentExceptionIsThrown() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.rateEvent(userId, eventId, -1));
        assertTrue(ex.getMessage().toLowerCase().contains("rating"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenRatingAboveFive_WhenRateEvent_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.rateEvent(userId, eventId, 6));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenAddEvent_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(null, companyId, "name", LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullCompanyId_WhenAddEvent_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addEvent(eventId, null, "name", LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenEditEvent_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.editEvent(null, null, null, null, null, null, null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenDeleteEvent_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.deleteEvent(null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenAddStandingTickets_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(null, UUID.randomUUID(), 5));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenZeroCount_WhenAddStandingTickets_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addStandingTickets(eventId, UUID.randomUUID(), 0));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenZeroRows_WhenAddSittingTickets_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.addSittingTickets(eventId, UUID.randomUUID(), 0, 4));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullEventId_WhenGetEventDetails_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> eventService.getEventDetails(null));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNullCompanyId_WhenSearchEventsByCompany_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.searchEventsByCompany(null, EventSearchCriteria.empty()));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenPriceMinGreaterThanPriceMax_WhenSearchEvents_ThenIllegalArgumentExceptionIsThrown() {
        EventSearchCriteria bad = EventSearchCriteria.empty().withPriceRange(200.0, 100.0);
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenNegativePriceMin_WhenSearchEvents_ThenIllegalArgumentExceptionIsThrown() {
        EventSearchCriteria bad = EventSearchCriteria.empty().withPriceRange(-1.0, 100.0);
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
        verifyNoInteractions(eventRepository);
    }

    @Test
    public void GivenDateFromAfterDateTo_WhenSearchEvents_ThenIllegalArgumentExceptionIsThrown() {
        LocalDateTime now = LocalDateTime.now();
        EventSearchCriteria bad = EventSearchCriteria.empty()
                .withDateRange(now.plusDays(10), now.plusDays(1));
        assertThrows(IllegalArgumentException.class, () -> eventService.searchEvents(bad));
    }

    @Test
    public void GivenMinEventRatingOutOfRange_WhenSearchEvents_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinEventRating(-1.0)));
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinEventRating(6.0)));
    }

    @Test
    public void GivenMinCompanyRatingOutOfRange_WhenSearchEvents_ThenIllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinCompanyRating(-0.5)));
        assertThrows(IllegalArgumentException.class, () ->
                eventService.searchEvents(EventSearchCriteria.empty().withMinCompanyRating(5.5)));
    }

    // =====================================================================
    // Pipe Tests — Service drives Domain and maps to DTOs
    // =====================================================================

    @Test
    public void GivenAuthorizedOwnerAndExistingEvent_WhenGetEventPurchaseHistoryForOwner_ThenReturnsHistoryDtos() {
        Event event = newRealEvent();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.isCompanyOwner(ownerUsername, companyId)).thenReturn(true);
        when(historyRepository.getByEventId(eventId)).thenReturn(Collections.emptyList());

        List<PurchaseHistoryDTO> actual =
                eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId);

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
        verify(historyRepository).getByEventId(eventId);
    }

    @Test
    public void GivenValidArgs_WhenAddEvent_ThenServiceInvokesSaveOnRepositoryAndReturnsDetails() {
        LocalDateTime date = LocalDateTime.now().plusDays(10);

        Event created = new Event(
                eventId,
                companyId,
                date,
                "Tel Aviv",
                "Some Artist",
                "concert",
                EventStatus.ACTIVE
        );

        when(eventRepository.getById(eventId))
                .thenReturn(null)
                .thenReturn(created);

        EventDetailsDto result = eventService.addEvent(
                eventId,
                companyId,
                "Headline Show",
                date,
                "Tel Aviv",
                "Some Artist",
                "concert",
                EventStatus.ACTIVE
        );

        assertNotNull(result);
        assertEquals(eventId, result.eventId());
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenExistingEvent_WhenEditEvent_ThenGetEventDetailsReturnsTheUpdatedFields() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        LocalDateTime newDate = LocalDateTime.now().plusDays(60);

        EventSummaryDto result = eventService.editEvent(eventId, "Renamed Show", newDate, "Haifa",
                "New Artist", "festival", EventStatus.CANCELED);

        assertNotNull(result);
        assertEquals("Renamed Show", result.name());
        assertEquals(newDate, result.date());
        assertEquals("Haifa", result.location());
        assertEquals("New Artist", result.artist());
        assertEquals("festival", result.eventType());
        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals("Renamed Show", details.name());
        assertEquals(newDate, details.date());
        assertEquals("Haifa", details.location());
        assertEquals("New Artist", details.artist());
        assertEquals("festival", details.eventType());
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenAllNullFieldsExceptId_WhenEditEvent_ThenReturnsTrueAndSavesEvent() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        EventSummaryDto result = eventService.editEvent(eventId, null, null, null, null, null, null);

        assertNotNull(result);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenExistingEvent_WhenDeleteEvent_ThenRepositoryDeleteIsInvokedAndReturnsTrue() {
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        boolean result = eventService.deleteEvent(eventId);

        assertTrue(result);
        verify(eventRepository).delete(eventId);
    }

    @Test
    public void GivenStandingArea_WhenAddStandingTickets_ThenServiceInvokesSaveOnRepository() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new StandingArea(areaId, 50.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addStandingTickets(eventId, areaId, 10);

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenSittingArea_WhenAddSittingTickets_ThenServiceInvokesSaveOnRepository() {
        Event event = newRealEvent();
        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new SittingArea(areaId, 100.0));
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.addSittingTickets(eventId, areaId, 3, 4);

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenValidArgs_WhenAddPolicyRule_ThenGetEventDetailsExposesThePersistedPurchaseRules() {
        stubAuthorizedRepositories(newRealEvent());

        // Add them sequentially to build a composite structure of 4 distinct components
        eventService.addPolicyRule(ownerUsername, companyId, eventId, Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);
        eventService.addPolicyRule(ownerUsername, companyId, eventId, Optional.empty(), Optional.of(1), Optional.empty(), Optional.empty(), true);
        eventService.addPolicyRule(ownerUsername, companyId, eventId, Optional.empty(), Optional.empty(), Optional.of(5), Optional.empty(), true);
        eventService.addPolicyRule(ownerUsername, companyId, eventId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), true);

        EventDetailsDto details = eventService.getEventDetails(eventId);

        // Assert that we have items exposed (toDetails flattens the rule tree
        // into one entry per leaf, so we expect at least one).
        assertFalse(details.purchasePolicy().rules().isEmpty());
    }

    @Test
    public void GivenValidArgs_WhenAddOvertDiscount_ThenGetEventDetailsExposesTheDiscount() {
        stubAuthorizedRepositories(newRealEvent());

        eventService.addOvertDiscount(ownerUsername, companyId, eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 10f);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals(1, details.discountPolicy().rules().size());
    }

    @Test
    public void GivenValidArgs_WhenAddConditionalDiscount_ThenGetEventDetailsExposesTheDiscount() {
        stubAuthorizedRepositories(newRealEvent());

        eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals(1, details.discountPolicy().rules().size());
    }

    @Test
    public void GivenValidArgs_WhenAddCouponCode_ThenGetEventDetailsExposesTheDiscount() {
        stubAuthorizedRepositories(newRealEvent());

        eventService.addCouponCode(ownerUsername, companyId, eventId,
                LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25");

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals(1, details.discountPolicy().rules().size());
    }

    @Test
    public void GivenExistingDiscountId_WhenRemoveDiscount_ThenGetEventDetailsReportsNoDiscount() {
        Event event = newRealEvent();
        event.addOvertDiscount(LocalDate.now(), LocalDate.now().plusDays(7), 10f);
        IDiscountRule existing = event.getDiscountPolicy().getDiscountRules().get(0);
        UUID realDiscountId = existing.getId();
        stubAuthorizedRepositories(event);

        eventService.removeDiscount(ownerUsername, companyId, eventId, realDiscountId);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals(0, details.discountPolicy().rules().size());
    }

    @Test
    public void GivenValidRating_WhenRateEvent_ThenGetEventDetailsReportsTheNewRating() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);

        eventService.rateEvent(userId, eventId, 4);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals("DTO must reflect the rating that was just submitted",
                4.0, details.rating(), 0.0001);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void GivenOneActiveCompanyWithVisibleEvents_WhenBrowseCatalog_ThenCatalogContainsItsEventsMappedToSummaries() {
        Company c = newActiveCompany("Acme");
        Event e1 = eventOf(c.getId(), "Show A", "Artist1", "concert",
                LocalDateTime.now().plusDays(10), "Tel Aviv", EventStatus.ACTIVE);
        Event e2 = eventOf(c.getId(), "Show B", "Artist2", "festival",
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

        assertTrue("empty catalog is the alternative flow, not an exception", catalog.isEmpty());
    }

    @Test
    public void GivenCompanyHasMixedStatusEvents_WhenBrowseCatalog_ThenOnlyActiveEventsAreIncluded() {
        Company c = newActiveCompany("Acme");
        Event active = eventOf(c.getId(), "A", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.ACTIVE);
        Event canceled = eventOf(c.getId(), "B", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.CANCELED);
        Event ended = eventOf(c.getId(), "C", "Ar", "t",
                LocalDateTime.now().plusDays(5), "Loc", EventStatus.ENDED);
        when(companyRepository.getAllActive()).thenReturn(Collections.singletonList(c));
        when(eventRepository.getAll()).thenReturn(Arrays.asList(active, canceled, ended));

        List<CompanyCatalogDto> catalog = eventService.browseCatalog();

        assertEquals(1, catalog.get(0).events().size());
        assertEquals(active.getEventId(), catalog.get(0).events().get(0).eventId());
    }

    @Test
    public void GivenExistingEventWithAreasAndPolicies_WhenGetEventDetails_ThenDtoIsFullyPopulated() {
        Event event = newRealEvent();
        event.setName("Headline Show");
        event.getLayout().addArea(new StandingArea(UUID.randomUUID(), 100.0));
        event.getLayout().addArea(new SittingArea(UUID.randomUUID(), 250.0));
        // Updated: Added boolean parameter 'andOr' to match Event signature
        event.addPurchasePolicy(Optional.of(18f), Optional.empty(), Optional.empty(), Optional.empty(), true);
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
        assertEquals(1, details.purchasePolicy().rules().size());
        assertEquals(1, details.discountPolicy().rules().size());
    }

    @Test
    public void GivenAllEventsBelongToActiveCompanies_WhenSearchEventsWithEmptyCriteria_ThenAllVisibleEventsAreReturnedAsSummaries() {
        Company c = newActiveCompany("Acme");
        Event e1 = eventOf(c.getId(), "X", "A1", "t1",
                LocalDateTime.now().plusDays(3), "Tel Aviv", EventStatus.ACTIVE);
        Event e2 = eventOf(c.getId(), "Y", "A2", "t2",
                LocalDateTime.now().plusDays(4), "Haifa", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(e1, e2));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(EventSearchCriteria.empty());

        assertEquals(2, results.size());
    }

    @Test
    public void GivenEventsBelongToInactiveCompany_WhenSearchEvents_ThenThoseEventsAreFilteredOut() {
        Company active = newActiveCompany("Active");
        Company inactive = newActiveCompany("Closed");
        inactive.AdminClose();
        Event visible = eventOf(active.getId(), "A", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        Event hidden = eventOf(inactive.getId(), "B", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(visible, hidden));
        when(companyRepository.findByID(active.getId())).thenReturn(Optional.of(active));
        when(companyRepository.findByID(inactive.getId())).thenReturn(Optional.of(inactive));

        List<EventSummaryDto> results = eventService.searchEvents(EventSearchCriteria.empty());

        assertEquals(1, results.size());
        assertEquals(visible.getEventId(), results.get(0).eventId());
    }

    @Test
    public void GivenTextCriteria_WhenSearchEvents_ThenResultsAreReturnedAsSummaryDtos() {
        Company c = newActiveCompany("Acme");
        Event hit = eventOf(c.getId(), "Coldplay Live", "X", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        Event miss = eventOf(c.getId(), "Show", "Other", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(hit, miss));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withText("coldplay"));

        assertEquals(1, results.size());
        assertEquals(hit.getEventId(), results.get(0).eventId());
        assertEquals("Coldplay Live", results.get(0).name());
    }

    @Test
    public void GivenNullCriteria_WhenSearchEvents_ThenServiceTreatsItAsEmptyCriteria() {
        Company c = newActiveCompany("Acme");
        Event e = eventOf(c.getId(), "X", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Collections.singletonList(e));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(null);

        assertEquals(1, results.size());
    }

    @Test
    public void GivenNoEventMatchesCriteria_WhenSearchEvents_ThenReturnsEmptyListAndDoesNotThrow() {
        Company c = newActiveCompany("Acme");
        Event e = eventOf(c.getId(), "Concert", "Artist", "concert",
                LocalDateTime.now().plusDays(5), "Tel Aviv", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Collections.singletonList(e));
        when(companyRepository.findByID(c.getId())).thenReturn(Optional.of(c));

        List<EventSummaryDto> results = eventService.searchEvents(
                EventSearchCriteria.empty().withText("nonexistent-keyword"));

        assertTrue("empty list is the expected alternative flow", results.isEmpty());
    }

    @Test
    public void GivenTwoCompaniesEachWithEvents_WhenSearchEventsByCompany_ThenResultsAreScopedToThatCompany() {
        Company a = newActiveCompany("A");
        Company b = newActiveCompany("B");
        Event a1 = eventOf(a.getId(), "A1", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event a2 = eventOf(a.getId(), "A2", "Ar", "t",
                LocalDateTime.now().plusDays(2), "Loc", EventStatus.ACTIVE);
        Event b1 = eventOf(b.getId(), "B1", "Ar", "t",
                LocalDateTime.now().plusDays(3), "Loc", EventStatus.ACTIVE);
        when(eventRepository.getAll()).thenReturn(Arrays.asList(a1, a2, b1));
        when(companyRepository.findByID(a.getId())).thenReturn(Optional.of(a));
        when(companyRepository.findByID(b.getId())).thenReturn(Optional.of(b));

        List<EventSummaryDto> results = eventService.searchEventsByCompany(
                a.getId(), EventSearchCriteria.empty());

        assertEquals(2, results.size());
        for (EventSummaryDto r : results) {
            assertEquals("every returned summary must belong to the requested company",
                    a.getId(), r.companyId());
        }
    }

    @Test
    public void GivenCriteriaWithText_WhenSearchEventsByCompany_ThenScopeAndTextFiltersBothApply() {
        Company a = newActiveCompany("A");
        Company b = newActiveCompany("B");
        Event aMatch = eventOf(a.getId(), "Coldplay", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event aOther = eventOf(a.getId(), "Other", "Ar", "t",
                LocalDateTime.now().plusDays(1), "Loc", EventStatus.ACTIVE);
        Event bMatch = eventOf(b.getId(), "Coldplay", "Ar", "t",
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
        when(eventRepository.getAll()).thenReturn(Collections.emptyList());

        List<EventSummaryDto> results = eventService.searchEventsByCompany(
                UUID.randomUUID(), EventSearchCriteria.empty());

        assertTrue(results.isEmpty());
    }

    // =====================================================================
    // Error Propagation — DomainException / IAE from Domain reaches Service
    // =====================================================================

    @Test
    public void GivenEventDoesNotExist_WhenGetEventPurchaseHistoryForOwner_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertEquals("Event not found", ex.getMessage());
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    @Test
    public void GivenUserIsNotOwnerOfEventCompany_WhenGetEventPurchaseHistoryForOwner_ThenDomainExceptionIsPropagated() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.isCompanyOwner(ownerUsername, companyId)).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class,
                () -> eventService.getEventPurchaseHistoryForOwner(ownerUsername, eventId));
        assertTrue(ex.getMessage().toLowerCase().contains("not authorized"));
        verify(historyRepository, never()).getByEventId(any(UUID.class));
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddPolicyRule_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Updated: Added trailing boolean flag parameter
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addPolicyRule(ownerUsername, companyId, eventId,
                        Optional.of(18f), Optional.of(1), Optional.of(5), Optional.of(true), true));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenDeletePolicyRule_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        // Updated: Uses targeted UUID instead of sequential primitive booleans
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.deletePolicyRule(ownerUsername, companyId, eventId, UUID.randomUUID()));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddOvertDiscount_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addOvertDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddConditionalDiscount_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addConditionalDiscount(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 10f, 3, 2));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenAddCouponCode_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.addCouponCode(ownerUsername, companyId, eventId,
                        LocalDate.now(), LocalDate.now().plusDays(7), 25f, "SUMMER25"));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenRemoveDiscount_ThenIllegalArgumentExceptionFromDomainIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.removeDiscount(ownerUsername, companyId, eventId, discountId));
        assertEquals("Event not found", ex.getMessage());
    }

    @Test
    public void GivenEventDoesNotExist_WhenRateEvent_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () -> eventService.rateEvent(userId, eventId, 4));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenUserHasAlreadyRatedTheEvent_WhenRateEvent_ThenDomainExceptionIsPropagatedAndNoSaveOccurs() {
        Event event = newRealEvent();
        event.addRating(userId, 3);
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertThrows(DomainException.class, () -> eventService.rateEvent(userId, eventId, 5));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenEventAlreadyExists_WhenAddEvent_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(newRealEvent());

        assertThrows(DomainException.class,
                () -> eventService.addEvent(eventId, companyId, "name", LocalDateTime.now().plusDays(10),
                        "Tel Aviv", "Some Artist", "concert", EventStatus.ACTIVE));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenEventDoesNotExist_WhenEditEvent_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class,
                () -> eventService.editEvent(eventId, null, null, null, null, null, null));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void GivenSaveThrowsRuntimeException_WhenRateEvent_ThenInfrastructureFailurePropagatesUnchanged() {
        Event event = newRealEvent();
        when(eventRepository.getById(eventId)).thenReturn(event);
        doThrow(new RuntimeException("DB down")).when(eventRepository).save(any(Event.class));

        DomainException ex = assertThrows(DomainException.class,
            () -> eventService.rateEvent(userId, eventId, 4));
        assertEquals("DB down", ex.getMessage());
    }


    @Test
    public void GivenEventDoesNotExist_WhenDeleteEvent_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () -> eventService.deleteEvent(eventId));
        verify(eventRepository, never()).delete(any(UUID.class));
    }

    @Test
    public void GivenUnknownEventId_WhenGetEventDetails_ThenDomainExceptionIsPropagated() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () -> eventService.getEventDetails(eventId));
    }

    // =====================================================================
    // Concurrency — invariants hold under contention, asserted via DTOs
    // =====================================================================

    @Test
    public void GivenManyConcurrentValidRatingsFromDistinctUsers_WhenRateEvent_ThenDtoRatingReflectsAllSubmissions()
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
        assertTrue("concurrent rating did not complete in time",
                done.await(15, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("no caller-visible exception is expected for valid ratings",
                0, leakedFailures.get());
        verify(eventRepository, times(expectedTotal)).save(event);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals("DTO rating must equal 4.0 since every accepted rating is 4",
                4.0, details.rating(), 0.0001);
    }

    @Test
    public void GivenMixedValidAndInvalidConcurrentRatings_WhenRateEvent_ThenOnlyValidRatingsAreVisibleInTheDto()
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
        assertTrue("mixed concurrent rating did not complete in time",
                done.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(0, unexpectedFailures.get());
        assertEquals("every invalid rating must surface as IllegalArgumentException",
                validCount, illegalCaught.get());
        verify(eventRepository, times(validCount)).save(event);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertEquals("only the rating=3 submissions should contribute to the DTO rating",
                3.0, details.rating(), 0.0001);
    }

    @Test
    public void GivenManyConcurrentDiscountRegistrations_WhenAddOvertDiscount_ThenServiceNeverSurfacesAnInternalError()
            throws InterruptedException {
        int threads = 20;
        Event event = newRealEvent();
        stubAuthorizedRepositories(event);

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
                    eventService.addOvertDiscount(ownerUsername, companyId, eventId, from, to, 15f);
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
        assertTrue("concurrent discount registration did not complete in time",
                done.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals("the application service must not raise an exception of its own",
                0, leakedFailures.get());
        verify(eventRepository, atLeast(1)).getById(eventId);

        EventDetailsDto details = eventService.getEventDetails(eventId);
        assertFalse("at least one discount must remain in the DTO after the storm",
                details.discountPolicy().rules().isEmpty());
    }

    @Test(timeout = 15000)
    public void GivenWritersAddingEventsAndReadersSearching_WhenRunConcurrently_ThenFinalSearchReturnsAllWritesAsDtos()
            throws Exception {
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
                                assertEquals("every visible summary must belong to the active company",
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
            assertTrue("threads must finish in time", done.await(10, TimeUnit.SECONDS));
        } finally {
            exec.shutdownNow();
        }

        assertEquals("no exceptions should be thrown by readers or writers", 0, errors.get());

        List<EventSummaryDto> finalSnapshot =
                eventService.searchEvents(EventSearchCriteria.empty());
        assertEquals("after settling, every persisted event must be reachable as a summary",
                writerCount * eventsPerWriter, finalSnapshot.size());
    }
}