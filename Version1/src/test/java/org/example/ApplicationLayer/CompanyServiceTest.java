package org.example.ApplicationLayer;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;

public class CompanyServiceTest {

    private ICompanyRepository companyRepositoryMock;
    private IUserRepository userRepositoryMock;

    private RolesDomainService rolesDomainService;
    private CompanyService companyService;

    private String adminUsername;
    private String regularUsername;
    private String memberUsername;
    private UUID companyId;

    @Before
    public void setUp() {
        companyRepositoryMock = mock(ICompanyRepository.class);
        userRepositoryMock = mock(IUserRepository.class);

        rolesDomainService = new RolesDomainService(companyRepositoryMock, userRepositoryMock);
        companyService = new CompanyService(rolesDomainService);

        adminUsername = "admin";
        regularUsername = "regularUser";
        memberUsername = "member";
        companyId = UUID.randomUUID();
    }

    /*
     * closeCompanyAsAdmin tests
     */

    @Test
    public void closeCompanyAsAdmin_whenAdminClosesActiveCompany_closesAndSavesCompany() {
        Company company = mock(Company.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
        when(company.isActive()).thenReturn(true);

        companyService.closeCompanyAsAdmin(adminUsername, companyId);

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(companyRepositoryMock).findByID(companyId);
        verify(company).AdminClose();
        verify(companyRepositoryMock).save(company);
    }

    @Test
    public void closeCompanyAsAdmin_whenAdminUsernameIsNull_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(null, companyId)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void closeCompanyAsAdmin_whenAdminUsernameIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(" ", companyId)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void closeCompanyAsAdmin_whenUserIsNotSystemAdmin_throwsExceptionAndDoesNotFetchCompany() {
        when(userRepositoryMock.isSystemAdmin(regularUsername)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.closeCompanyAsAdmin(regularUsername, companyId)
        );

        verify(userRepositoryMock).isSystemAdmin(regularUsername);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyDoesNotExist_throwsExceptionAndDoesNotSave() {
        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () ->
                companyService.closeCompanyAsAdmin(adminUsername, companyId)
        );

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(companyRepositoryMock).findByID(companyId);
        verify(companyRepositoryMock, never()).save(any());
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyAlreadyInactive_throwsExceptionAndDoesNotCloseAgain() {
        Company company = mock(Company.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
        when(company.isActive()).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                companyService.closeCompanyAsAdmin(adminUsername, companyId)
        );

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(companyRepositoryMock).findByID(companyId);
        verify(company, never()).AdminClose();
        verify(companyRepositoryMock, never()).save(any());
    }

    /*
     * removeCompanyMemberAsAdmin tests
     */

    @Test
    public void removeCompanyMemberAsAdmin_whenAdminRemovesExistingMember_removesFromAllCompaniesAndSaves() {
        Company firstCompany = mock(Company.class);
        Company secondCompany = mock(Company.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.getCompaniesByMember(memberUsername))
                .thenReturn(List.of(firstCompany, secondCompany));

        companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(companyRepositoryMock).getCompaniesByMember(memberUsername);

        verify(firstCompany).removeMemberAsAdmin(memberUsername);
        verify(secondCompany).removeMemberAsAdmin(memberUsername);

        verify(companyRepositoryMock).save(firstCompany);
        verify(companyRepositoryMock).save(secondCompany);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenAdminUsernameIsNull_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(null, memberUsername)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenAdminUsernameIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(" ", memberUsername)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUsernameToRemoveIsNull_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, null)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUsernameToRemoveIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, " ")
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotSystemAdmin_throwsExceptionAndDoesNotFetchCompanies() {
        when(userRepositoryMock.isSystemAdmin(regularUsername)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(regularUsername, memberUsername)
        );

        verify(userRepositoryMock).isSystemAdmin(regularUsername);
        verify(companyRepositoryMock, never()).getCompaniesByMember(anyString());
        verify(companyRepositoryMock, never()).save(any());
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_throwsExceptionAndDoesNotSave() {
        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.getCompaniesByMember(memberUsername)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername)
        );

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(companyRepositoryMock).getCompaniesByMember(memberUsername);
        verify(companyRepositoryMock, never()).save(any());
    }
}