package org.example.ApplicationLayer;

import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CompanyServiceTest {

    private RolesDomainService rolesDomainServiceMock;
    private PurchaseDomainService purchaseDomainServiceMock;
    private CompanyService companyService;

    @Before
    public void setUp() {
        rolesDomainServiceMock = mock(RolesDomainService.class);
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        companyService = new CompanyService(rolesDomainServiceMock, purchaseDomainServiceMock);
    }

    /* Test cases for closeCompanyAsAdmin method */
    @Test
    public void testSuccessfulCompanyClosure() {
        String adminUsername = "admin";
        UUID companyId = UUID.randomUUID();

        companyService.closeCompanyAsAdmin(adminUsername, companyId);

        verify(rolesDomainServiceMock, times(1))
                .closeCompanyAsAdmin(adminUsername, companyId);
    }

    @Test
    public void testCompanyNotFound() {
        String adminUsername = "admin";
        UUID companyId = UUID.randomUUID();

        doThrow(new IllegalArgumentException("Company does not exist"))
                .when(rolesDomainServiceMock)
                .closeCompanyAsAdmin(adminUsername, companyId);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(adminUsername, companyId)
        );
    }

    @Test
    public void testCompanyAlreadyClosed() {
        String adminUsername = "admin";
        UUID companyId = UUID.randomUUID();

        doThrow(new IllegalArgumentException("Company is already closed"))
                .when(rolesDomainServiceMock)
                .closeCompanyAsAdmin(adminUsername, companyId);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(adminUsername, companyId)
        );
    }

    @Test
    public void testUnauthorizedCompanyClosure() {
        String username = "regularUser";
        UUID companyId = UUID.randomUUID();

        doThrow(new IllegalArgumentException("User is not an admin"))
                .when(rolesDomainServiceMock)
                .closeCompanyAsAdmin(username, companyId);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(username, companyId)
        );
    }

    @Test
    public void testCloseCompany_AdminUsernameIsNull() {
        UUID companyId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(null, companyId)
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }

    @Test
    public void testCloseCompany_AdminUsernameIsBlank() {
        UUID companyId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(" ", companyId)
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }


    /* Test cases for removeCompanyMemberAsAdmin method */

    @Test
    public void testSuccessfulUserRemoval() {
        String adminUsername = "admin";
        String usernameToRemove = "member";

        companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        verify(rolesDomainServiceMock, times(1))
                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);
    }

    @Test
    public void testUserNotFound() {
        String adminUsername = "admin";
        String usernameToRemove = "missingUser";

        doThrow(new IllegalArgumentException("User not found"))
                .when(rolesDomainServiceMock)
                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove)
        );
    }

    @Test
    public void testUserIsNotMember() {
        String adminUsername = "admin";
        String usernameToRemove = "guest";

        doThrow(new IllegalArgumentException("User is not a member"))
                .when(rolesDomainServiceMock)
                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove)
        );
    }

    @Test
    public void testUnauthorizedUserRemoval() {
        String adminUsername = "regularUser";
        String usernameToRemove = "member";

        doThrow(new IllegalArgumentException("User is not system admin"))
                .when(rolesDomainServiceMock)
                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove)
        );
    }

    @Test
    public void testRemoveCompanyMember_AdminUsernameIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(null, "member")
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }

    @Test
    public void testRemoveCompanyMember_AdminUsernameIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(" ", "member")
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }

    @Test
    public void testRemoveCompanyMember_UsernameToRemoveIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin("admin", null)
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }

    @Test
    public void testRemoveCompanyMember_UsernameToRemoveIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin("admin", " ")
        );

        verifyNoInteractions(rolesDomainServiceMock);
    }
}