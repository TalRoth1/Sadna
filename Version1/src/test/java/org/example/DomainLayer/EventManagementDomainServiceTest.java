package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.User;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private User managerUserWithEvent(UUID managedEventId) {
        User user = new User(UUID.randomUUID(), username, username + "@example.test", "hash", 40);
        user.getCompanyRoles().put(companyId, new CompanyFounder(username));
        user.getCompanyRole(companyId).getEventsIds().add(managedEventId);
        return user;
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
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(managerUserWithEvent(eventId)));

        service.addEvent(
                eventId,
                companyId,
            username,
                "My Event",
                LocalDateTime.now().plusDays(7),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE,
                "description"
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
                username,
                        "My Event",
                        LocalDateTime.now(),
                        "Tel Aviv",
                        "Artist",
                        "Concert",
                        EventStatus.ACTIVE,
                        "description"
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
                EventStatus.CANCELED,
                "Updated description"
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
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(managerUserWithEvent(eventId)));

        boolean result = service.deleteEvent(eventId, username, username);

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

    @Test
    public void updateStandingArea_whenUserHasManageInventoryPermission_updatesAreaAndSavesEvent() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(true);

        service.updateStandingArea(username, companyId, eventId, areaId, 120.0, 50);

        verify(event).updateStandingArea(areaId, 120.0, 50);
        verify(eventRepository).save(event);
    }

    @Test
    public void updateStandingArea_whenUserHasConfigureLayoutPermission_updatesAreaAndSavesEvent() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(false);
        when(userRepository.hasPermission(username, companyId, CompanyPermission.CONFIGURE_LAYOUT, eventId))
                .thenReturn(true);

        service.updateStandingArea(username, companyId, eventId, areaId, 120.0, 50);

        verify(event).updateStandingArea(areaId, 120.0, 50);
        verify(eventRepository).save(event);
    }

    @Test
    public void updateStandingArea_whenUserHasNoInventoryOrLayoutPermission_throwsAndDoesNotSave() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(false);
        when(userRepository.hasPermission(username, companyId, CompanyPermission.CONFIGURE_LAYOUT, eventId))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                service.updateStandingArea(username, companyId, eventId, areaId, 120.0, 50)
        );

        verify(event, never()).updateStandingArea(any(), any(Double.class), any(Integer.class));
        verify(eventRepository, never()).save(any());
    }

    @Test
    public void updateSittingArea_whenUserHasManageInventoryPermission_updatesAreaAndSavesEvent() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(true);

        service.updateSittingArea(username, companyId, eventId, areaId, 180.0, 20, 10);

        verify(event).updateSittingArea(areaId, 180.0, 20, 10);
        verify(eventRepository).save(event);
    }

    @Test
    public void deleteArea_whenUserHasManageInventoryPermission_deletesAreaAndSavesEvent() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(true);

        service.deleteArea(username, companyId, eventId, areaId);

        verify(event).deleteArea(areaId);
        verify(eventRepository).save(event);
    }

    @Test
    public void deleteArea_whenUserHasNoInventoryOrLayoutPermission_throwsAndDoesNotSave() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId))
                .thenReturn(false);
        when(userRepository.hasPermission(username, companyId, CompanyPermission.CONFIGURE_LAYOUT, eventId))
                .thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                service.deleteArea(username, companyId, eventId, areaId)
        );

        verify(event, never()).deleteArea(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    public void updateSittingArea_whenEventDoesNotExist_throwsAndDoesNotSave() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(null);

        assertThrows(DomainException.class, () ->
                service.updateSittingArea(username, companyId, eventId, areaId, 180.0, 20, 10)
        );

        verify(eventRepository, never()).save(any());
    }

    @Test
    public void updateStandingArea_whenCompanyDoesNotExist_throwsAndDoesNotSave() {
        UUID areaId = UUID.randomUUID();

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.updateStandingArea(username, companyId, eventId, areaId, 120.0, 50)
        );

        verify(event, never()).updateStandingArea(any(), any(Double.class), any(Integer.class));
        verify(eventRepository, never()).save(any());
    }
}
