package org.example.DomainLayer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class RolesDomainService {

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    private final ConcurrentHashMap<UUID, ReentrantLock> companyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public RolesDomainService(ICompanyRepository companyRepository, IUserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public UUID createCompany(String founderUsername, String companyName) {
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

    public UUID inviteCompanyManager(String ownerUsername, UUID companyId, String usernameToInvite,
            Set<CompanyPermission> premissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (usernameToInvite == null || usernameToInvite.isBlank()) {
            throw new IllegalArgumentException("Username to invite is required");
        }
        Company company = companyRepository.findByID(companyId).get();

        if (company == null)
            throw new IllegalArgumentException("Company not found");

        // Fetch and validate the appointer first so authorization errors are raised
        // before any user-to-invite existence checks.
        User ownerUser = userRepository.findByEmail(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

        if (!ownerUser.isCompanyMember(companyId) || !ownerUser.isOwnerInCompany(companyId)) {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot invite a new manager");
        }

        User userToInvite = userRepository.findByEmail(usernameToInvite)
                .orElseThrow(() -> new IllegalArgumentException("User to invite not found"));

        synchronized (company) {
            return userToInvite.inviteUserToBecomeManager(companyId, ownerUser, premissions);
        }
    }

    public UUID inviteCompanyOwner(String ownerUsername, UUID companyId, String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (usernameToInvite == null || usernameToInvite.isBlank()) {
            throw new IllegalArgumentException("Username to invite is required");
        }
        Company company = companyRepository.findByID(companyId).get();

        if (company == null)
            throw new IllegalArgumentException("Company not found");

        User ownerUser = userRepository.findByEmail(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

        if (!ownerUser.isCompanyMember(companyId) || !ownerUser.isOwnerInCompany(companyId)) {
            throw new IllegalArgumentException("The appointer is not a company owner and therefore cannot invite a new owner");
        }

        User userToInvite = userRepository.findByEmail(usernameToInvite)
                .orElseThrow(() -> new IllegalArgumentException("User to invite not found"));

        synchronized (company) {
            return userToInvite.inviteUserToBecomeOwner(companyId, ownerUser);
        }
    }

    public void acceptCompanyInvitation(UUID invetationID, String username, UUID companyId) {
        Company company = companyRepository.findByID(companyId).get();

        if (company == null)
            throw new IllegalArgumentException("Company not found");

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        synchronized (company) {
            user.acceptCompanyInvitation(invetationID);
        }
    }

    public void addPurchasePolicy(String username, UUID companyId, Optional<Float> age, Optional<Integer> minTicket,
        Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat, boolean andOr) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
        company.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat, andOr);
    }

    public void deletePurchasePolicy(String username, UUID companyId, UUID ruleId) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
        company.deletePurchaseRule(ruleId);
    }

    public void addOvertDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate,
            float discountPrecent) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
        company.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate,
            float discountPrecent,
            int requiredTickets, int appliedTickets) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
        company.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId, LocalDate fromDate, LocalDate toDate,
            float discountPrecent,
            String code) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
        company.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID discountId) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ICompanyMember userRole = user.getCompanyRole(companyId);
        if (!(userRole instanceof CompanyManager
                && ((CompanyManager) userRole).hasPremission(CompanyPermission.MANAGE_POLICIES))
                && !(userRole instanceof CompanyOwner) && !(userRole instanceof CompanyFounder)) {
            throw new IllegalArgumentException("User has no permission to change company policies");
        }
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
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User managerUser = userRepository.findByEmail(managerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Manager user not found"));
        User ownerUser = userRepository.findByEmail(ownerUsername)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));
        synchronized (company) {
            managerUser.changeManagerPermissionsAsOwner(companyId, ownerUser, newPremissions);
        }
    }

    public void rateCompany(UUID userID, UUID companyID, int rating) {
        Company company = companyRepository.findByID(companyID).get();

        if (company == null)
            throw new DomainException("Event not found while rating");

        if (userID == null)
            throw new DomainException("User not found while rating");

        company.addRating(userID, rating);
        companyRepository.save(company);
    }

    public String getCompanyHierarchyMermaid(UUID companyId, String requesterUsername) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User requesterUser = userRepository.findByEmail(requesterUsername)
                .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        return requesterUser.getHierarchyMermaid(companyId);
    }
}
