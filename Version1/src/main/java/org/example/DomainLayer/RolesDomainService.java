package org.example.DomainLayer;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyManager;
import org.example.DomainLayer.CompanyAggregate.CompanyOwner;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.CompanyAggregate.ICompanyMember;
import org.example.DomainLayer.CompanyAggregate.Invitation;
import org.example.DomainLayer.UserAggregate.User;

public class RolesDomainService {

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    private final ConcurrentHashMap<UUID, ReentrantLock> companyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public RolesDomainService(ICompanyRepository companyRepository, IUserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public Company createCompany(String founderUsername, String companyName) {
        return companyRepository.createCompany(founderUsername, companyName);
    }

    public void closeCompanyAsAdmin(String adminUsername, UUID companyId) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        ReentrantLock lock = companyLocks.computeIfAbsent(companyId, key -> new ReentrantLock());
        lock.lock();

        try {
            if (!userRepository.isSystemAdmin(adminUsername)) {
                throw new IllegalArgumentException("User is not system admin");
            }

            Company company = companyRepository.findByID(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));

            if (!company.isActive()) {
                throw new IllegalStateException("Company already inactive");
            }

            company.AdminClose();
            companyRepository.save(company);
            // TODO: add notification to company members

        } finally {
            lock.unlock();
        }
    }

    public void removeCompanyMemberAsAdmin(String adminUsername, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (usernameToRemove == null || usernameToRemove.isBlank()) {
            throw new IllegalArgumentException("Username to remove is required");
        }

        ReentrantLock lock = userLocks.computeIfAbsent(usernameToRemove, key -> new ReentrantLock());
        lock.lock();

        try {
            if (!userRepository.isSystemAdmin(adminUsername)) {
                throw new IllegalArgumentException("User is not system admin");
            }

            User user = userRepository.findByEmail(usernameToRemove)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<UUID> companies = userRepository.getCompaniesIdsByMember(usernameToRemove);
            if (companies.isEmpty()) {
                throw new IllegalArgumentException("User is not assigned to any company");
            }

            for (UUID companyId : companies) {
                Company company = companyRepository.findByID(companyId)
                        .orElseThrow(() -> new IllegalArgumentException("Company not found"));
                synchronized (company) {
                    user.removeFromCompanyAsAdmin(companyId);
                }
            }
            // TODO: add notification to company members

        } finally {
            lock.unlock();
        }
    }

    public void removeCompanyMemberAsOwner(String ownerUsername, UUID companyId, String usernameToRemove) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (usernameToRemove == null || usernameToRemove.isBlank()) {
            throw new IllegalArgumentException("Username to remove is required");
        }

        // making sure the company exists
        Company company = companyRepository.findByID(companyId).get();
        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }

        User userToRemove = userRepository.findByEmail(usernameToRemove)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User ownerUser = userRepository.findByEmail(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

        synchronized (company) {
            userToRemove.removeFromCompanyAsOwner(companyId, ownerUser);
        }
    }

    public Invitation inviteCompanyManager(String ownerUsername, UUID companyId, String usernameToInvite,
                                           Set<CompanyPermission> premissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (usernameToInvite == null || usernameToInvite.isBlank()) {
            throw new IllegalArgumentException("Username to invite is required");
        }

        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        UUID invitationId = company.inviteNewManager(usernameToInvite, ownerUsername, premissions);
        return company.getInvitation(invitationId);
    }

    public Invitation inviteCompanyOwner(String ownerUsername, UUID companyId, String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (usernameToInvite == null || usernameToInvite.isBlank()) {
            throw new IllegalArgumentException("Username to invite is required");
        }

        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        UUID invitationId = company.inviteNewOwner(usernameToInvite, ownerUsername);
        return company.getInvitation(invitationId);
    }

    public void acceptCompanyInvitation(UUID invetationID, UUID companyId) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Invitation invitation = company.getInvitation(invetationID);
        if (invitation == null) {
            throw new IllegalArgumentException("Invitation not found");
        }

        String username = invitation.getAppointeeUsername();

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        synchronized (company) {
            user.acceptCompanyInvitation(invetationID);
        }
    }

    public void addPurchasePolicy(String username, UUID companyId,
                                  Float age, Integer minTicket,
                                  Integer maxTicket, Boolean allowLoneSeat) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePurchasePolicy(String username, UUID companyId,
                                     boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.deletePurchaseRule(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void addOvertDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent,
                                       int requiredTickets, int appliedTickets) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent,
                              String code) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID discountId) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        requirePolicyPermission(company, username);
        company.removeDiscount(discountId);
    }

    public void changeManagerPermissions(String ownerUsername, UUID companyId, String managerUsername,
            Set<CompanyPermission> newPremissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (managerUsername == null || managerUsername.isBlank()) {
            throw new IllegalArgumentException("Manager username is required");
        }

        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        company.changeManagerPermissions(ownerUsername, managerUsername, newPremissions);
    }

    public void rateCompany(UUID userID, UUID companyID, int rating) {
        if (userID == null) {
            throw new DomainException("User not found while rating");
        }

        Company company = companyRepository.findByID(companyID)
                .orElseThrow(() -> new DomainException("Company not found while rating"));

        company.addRating(userID, rating);
        companyRepository.save(company);
    }

    public String getCompanyHierarchyMermaid(UUID companyId, String requesterUsername) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        return company.getHierarchyMermaid(requesterUsername);
    }

    // ================================================================
    //  PRIVATE HELPERS
    // ================================================================

    /**
     * Verifies that the user is allowed to manage purchase / discount policies.
     * A user is allowed if either:
     *   - They are a CompanyOwner (this includes CompanyFounder, since it extends CompanyOwner)
     *   - They are a CompanyManager with the MANAGE_POLICIES permission
     *
     * Throws IllegalArgumentException if the user has no permission.
     */
    private void requirePolicyPermission(Company company, String username) {
        ICompanyMember user = company.getMember(username);

        boolean isManagerWithPermission = user instanceof CompanyManager
                && ((CompanyManager) user).hasPremission(CompanyPermission.MANAGE_POLICIES);
        boolean isOwnerOrFounder = user instanceof CompanyOwner; // CompanyFounder extends CompanyOwner

        if (!isManagerWithPermission && !isOwnerOrFounder) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
    }
}
