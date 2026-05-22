package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.*;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class EventManagementDomainServiceTest {

    private IEventRepository eventRepository;
    private IHistoryRepository historyRepository;
    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private EventManagementDomainService service;

    private UUID eventId;
    private UUID companyId;
    private String username;
    private Event event;
    private Company company;

    @Before
    public void setUp() {
        eventRepository = mock(IEventRepository.class);
        historyRepository = mock(IHistoryRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        userRepository = mock(IUserRepository.class);

        service = new EventManagementDomainService(
                eventRepository,
                historyRepository,
                companyRepository,
                userRepository
        );

        eventId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        username = "owner";

        event = mock(Event.class);
        company = mock(Company.class);

        when(event.getCompanyId()).thenReturn(companyId);
    }

    @Test
    public void getEventPurchaseHistory_whenEventExistsAndUserIsOwner_returnsHistory() {
        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.isCompanyOwner(username, companyId)).thenReturn(true);
        when(historyRepository.getByEventId(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                service.getEventPurchaseHistory(username, eventId);

        assertSame(expected, result);
        verify(historyRepository).getByEventId(eventId);
    }

    @Test
    public void getEventPurchaseHistory_whenEventDoesNotExist_throwsException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () ->
                service.getEventPurchaseHistory(username, eventId)
        );

        verifyNoInteractions(historyRepository);
    }

    @Test
    public void getEventPurchaseHistory_whenUserIsNotOwner_throwsException() {
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.isCompanyOwner(username, companyId)).thenReturn(false);

        assertThrows(DomainException.class, () ->
                service.getEventPurchaseHistory(username, eventId)
        );

        verify(historyRepository, never()).getByEventId(any());
    }

@Test
    public void addPurchasePolicy_whenUserHasPermission_addsPolicyToEvent() {
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
                .thenReturn(true);

        service.addPurchasePolicy(
                username,
                companyId,
                eventId,
                Optional.of(18f),
                Optional.of(1),
                Optional.of(5),
                Optional.of(true),
                true // Appended composite composition binary flag
        );

        verify(event).addPurchasePolicy(
                Optional.of(18f),
                Optional.of(1),
                Optional.of(5),
                Optional.of(true),
                true // Verified expected method invocation matching signature
        );
    }

    @Test
    public void addPurchasePolicy_whenEventDoesNotExist_throwsException() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                service.addPurchasePolicy(
                        username,
                        companyId,
                        eventId,
                        Optional.of(18f),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true // Appended composition binary flag
                )
        );
    }

    @Test
    public void addPurchasePolicy_whenUserHasNoPermission_throwsException() {
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                service.addPurchasePolicy(
                        username,
                        companyId,
                        eventId,
                        Optional.of(18f),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true // Appended composition binary flag
                )
        );

        // Verification uses anyBoolean() or explicit true/false to match the updated 5-argument method
        verify(event, never()).addPurchasePolicy(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    public void rateEvent_whenEventExists_addsRatingAndSavesEvent() {
        UUID userId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);

        service.rateEvent(userId, eventId, 5);

        verify(event).addRating(userId, 5);
        verify(eventRepository).save(event);
    }

    @Test
    public void rateEvent_whenEventDoesNotExist_throwsException() {
        UUID userId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () ->
                service.rateEvent(userId, eventId, 5)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    public void addEvent_whenEventDoesNotExist_createsAndSavesEvent() {
        when(eventRepository.getById(eventId)).thenReturn(null);

        service.addEvent(
                eventId,
                companyId,
                "My Event",
                LocalDateTime.now().plusDays(7),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE
        );

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    public void addEvent_whenEventAlreadyExists_throwsException() {
        when(eventRepository.getById(eventId)).thenReturn(event);

        assertThrows(DomainException.class, () ->
                service.addEvent(
                        eventId,
                        companyId,
                        "My Event",
                        LocalDateTime.now(),
                        "Tel Aviv",
                        "Artist",
                        "Concert",
                        EventStatus.ACTIVE
                )
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    public void editEvent_whenEventExists_updatesOnlyNonNullFieldsAndSaves() {
        LocalDateTime newDate = LocalDateTime.now().plusDays(10);

        when(eventRepository.getById(eventId)).thenReturn(event);

        Set<UUID> result = service.editEvent(
                eventId,
                null,
                newDate,
                "Haifa",
                null,
                "Festival",
                EventStatus.CANCELED
        );

        verify(event).setDate(newDate);
        verify(event).setLocation("Haifa");
        verify(event, never()).setArtist(any());
        verify(event).setType("Festival");
        verify(event).setStatus(EventStatus.CANCELED);
        verify(eventRepository).save(event);
    }

    @Test
    public void deleteEvent_whenEventExists_deletesEvent() {
        when(eventRepository.getById(eventId)).thenReturn(event);

        boolean result = service.deleteEvent(eventId);

        assertTrue(result);
        verify(eventRepository).delete(eventId);
    }

    @Test
    public void addStandingTickets_whenEventExists_addsTicketsAndSaves() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);

        service.addStandingTickets(eventId, areaId, 3);

        verify(event).addStandingTickets(areaId, 3);
        verify(eventRepository).save(event);
    }

    @Test
    public void addSittingTickets_whenEventExists_addsTicketsAndSaves() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);

        service.addSittingTickets(eventId, areaId, 2, 4);

        verify(event).addSittingTickets(areaId, 2, 4);
        verify(eventRepository).save(event);
    }
}