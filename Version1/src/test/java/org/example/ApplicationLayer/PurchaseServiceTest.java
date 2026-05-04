package org.example.ApplicationLayer;

import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PurchaseServiceTest {

    private PurchaseDomainService purchaseDomainServiceMock;
    private PurchaseService purchaseService;

    @Before
    public void setUp() {
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        purchaseService = new PurchaseService(purchaseDomainServiceMock);
    }

    /*
     * Admin purchase history filter tests
     */

    @Test
    public void getHistoryByFilter_whenFilterIsUser_returnsUserHistory() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByUser(userId);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsEvent_returnsEventHistory() {
        UUID adminId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "event", eventId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByEvent(eventId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsCompany_returnsCompanyHistory() {
        UUID adminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByCompany(companyId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "company", companyId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByCompany(companyId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsAll_returnsAllHistory() {
        UUID adminId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(
                mock(PurchaseHistory.class),
                mock(PurchaseHistory.class)
        );

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getAllHistory()).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "all", null);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getAllHistory();
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
    }

    @Test
    public void getHistoryByFilter_whenUserHistoryIsEmpty_returnsEmptyList() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertTrue(result.isEmpty());

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByUser(userId);
    }

    /*
     * Admin purchase history
     */

    @Test
    public void getHistoryByFilter_whenAdminIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(null, "user", UUID.randomUUID())
        );

        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenAdminIsInvalid_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", UUID.randomUUID())
        );

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, null, UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsBlank_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, " ", UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsInvalid_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "invalid", UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenUserFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", null)
        );

        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verifyNoInteractions(purchaseDomainServiceMock);
    }

    @Test
    public void getHistoryByFilter_whenEventFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "event", null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
    }

    @Test
    public void getHistoryByFilter_whenCompanyFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "company", null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
    }

    /*
     * Member purchase history tests
     */

    @Test
    public void getPurchaseHistoryForMember_whenMemberIsValidAndLoggedIn_returnsMemberHistory() {
        UUID memberId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock).isMember(memberId);
        verify(purchaseDomainServiceMock).isMemberLoggedIn(memberId);
        verify(purchaseDomainServiceMock).getPurchaseHistoryForMember(memberId);
    }

    @Test
    public void getPurchaseHistoryForMember_whenHistoryIsEmpty_returnsEmptyList() {
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
    public void getPurchaseHistoryForMember_whenMemberIdIsNull_throwsExceptionAndDoesNotTouchDomain() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberDoesNotExist_throwsExceptionAndDoesNotFetchHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock, never()).isMember(any());
        verify(purchaseDomainServiceMock, never()).isMemberLoggedIn(any());
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    @Test
    public void getPurchaseHistoryForMember_whenUserIsNotMember_throwsExceptionAndDoesNotFetchHistory() {
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(userId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(userId)
        );

        verify(purchaseDomainServiceMock).memberExists(userId);
        verify(purchaseDomainServiceMock).isMember(userId);
        verify(purchaseDomainServiceMock, never()).isMemberLoggedIn(any());
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberIsNotLoggedIn_throwsExceptionAndDoesNotFetchHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock).isMember(memberId);
        verify(purchaseDomainServiceMock).isMemberLoggedIn(memberId);
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    /*
     * Owner event purchase history tests
     */

    @Test
    public void getEventPurchaseHistoryForOwner_whenOwnerOwnsEvent_returnsEventHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock).isCompanyOwnerOfEvent(ownerName, eventId);
        verify(purchaseDomainServiceMock).getHistoryByEvent(eventId);
    }

    @Test
    public void getEventPurchaseHistoryForOwner_whenEventDoesNotExist_throwsExceptionAndDoesNotFetchHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock, never()).isCompanyOwnerOfEvent(anyString(), any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
    }

    @Test
    public void getEventPurchaseHistoryForOwner_whenOwnerDoesNotOwnEvent_throwsExceptionAndDoesNotFetchHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock).isCompanyOwnerOfEvent(ownerName, eventId);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
    }
}