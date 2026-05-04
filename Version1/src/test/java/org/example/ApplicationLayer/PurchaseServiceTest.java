package org.example.ApplicationLayer;

import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PurchaseServiceTest {
    private PurchaseDomainService purchaseDomainServiceMock;
    private PurchaseService purchaseService;
    
    @Mock
    private QueueManager queueManagerMock;

    @Before
    public void setUp() {
        queueManagerMock = mock(QueueManager.class);
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        purchaseService = new PurchaseService(purchaseDomainServiceMock, queueManagerMock);
    }

    /* Tests for viewing purchase history by filter */

    @Test
    public void testViewPurchaseHistoryByFilter_UserSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_EventSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "event", eventId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_CompanySuccess() {
        UUID adminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByCompany(companyId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "company", companyId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_AllSuccess() {
        UUID adminId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getAllHistory()).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "all", null);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_NullFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, null, UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_BlankFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, " ", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_UserMissingFilterId() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", null)
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_InvalidFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "invalid", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_UnauthorizedUser() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(null, "user", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_EmptyResult() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertTrue(result.isEmpty());
    }

    /* Tests for viewing purchase history for member */

    @Test
    public void testSuccessfulViewPurchaseHistory() {
        UUID memberId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertEquals(expected, result);
    }
    @Test
    public void testPurchaseHistoryUserNotFound() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );
    }

    @Test
    public void testEmptyPurchaseHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testPurchaseHistoryUserNotLoggedIn() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );
    }

    @Test
    public void testPurchaseHistoryMemberIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(null)
        );
    }

    @Test
    public void testPurchaseHistory_UserIsNotMember() {
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(userId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(userId)
        );
    }


    /* Tests for viewing event purchase history for owner */
    @Test
    public void testGetEventPurchaseHistoryForOwner_Success() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertEquals(expected, result);
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_EventDoesNotExist() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_UnauthorizedOwner() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_CancelledEventStillReturnsHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertEquals(expected, result);
    }
}
