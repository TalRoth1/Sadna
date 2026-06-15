package org.example.DomainLayer;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyMembershipResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.Invitation;
import org.example.DomainLayer.UserAggregate.ManagerInvitation;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.OwnerInvitation;
import org.example.DomainLayer.UserAggregate.User;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;


@Service
public class RolesDomainService {

    private static final Logger log = Logger.getLogger(RolesDomainService.class.getName());

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    private final ConcurrentHashMap<UUID, ReentrantLock> companyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public RolesDomainService(ICompanyRepository companyRepository, IUserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        log.warning("[DIAG] companyRepository impl = " + companyRepository.getClass().getName());
        log.warning("[DIAG] userRepository impl    = " + userRepository.getClass().getName());
    }

    public UUID createCompany(String founderEmail, String companyName, DiscountType discountType) {
        if (founderEmail == null || founderEmail.isBlank()) {
            throw new IllegalArgumentException("Founder email is required");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (discountType == null) {
            throw new IllegalArgumentException("Discount type is required");
        }

        User founder = userRepository.findByEmail(founderEmail)
                .orElseThrow(() -> new IllegalArgumentException("Founder user not found"));

        UUID companyId = companyRepository.createCompany(founderEmail, companyName, discountType);

        synchronized (founder) {
            founder.getCompanyRoles().put(companyId, new CompanyFounder(founderEmail));
            userRepository.add(founder);
        }

        return companyId;
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

    public String getCompanyOwner(UUID companyId)
    {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        Company company = companyRepository.findByID(companyId).get();
        return company.getFounderEmail();
    }

    public Company getCompany(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        return companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
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

            userRepository.add(user);
// TODO: add notification to company members

        } finally {
            lock.unlock();
        }
    }

    public void removeCompanyMemberAsOwner(String ownerEmail, UUID companyId, String emailToRemove) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Owner email is required");
        }

        if (emailToRemove == null || emailToRemove.isBlank()) {
            throw new IllegalArgumentException("Email to remove is required");
        }
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        User userToRemove = userRepository.findByEmail(emailToRemove)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User ownerUser = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

        synchronized (company) {
            userToRemove.removeFromCompanyAsOwner(companyId, ownerUser);
            userRepository.add(userToRemove);
            userRepository.add(ownerUser);
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
            UUID invitationId = userToInvite.inviteUserToBecomeManager(companyId, ownerUser, premissions);
            userRepository.add(userToInvite);
            return invitationId;
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
            UUID invitationId = userToInvite.inviteUserToBecomeOwner(companyId, ownerUser);
            userRepository.add(userToInvite);
            return invitationId;
        }
    }

    public void acceptCompanyInvitation(UUID invetationID, String username, UUID companyId) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        synchronized (company) {
            Invitation invitation = user.getCompanyInvitations()
                    .stream()
                    .filter(inv -> inv.getId().equals(invetationID))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Invalid invitation ID."));

            User appointerUser = invitation.getAppointerUser();

            user.acceptCompanyInvitation(invetationID);

            userRepository.add(user);

            if (appointerUser != null) {
                userRepository.add(appointerUser);
            }
        }
    }

    public void rejectCompanyInvitation(UUID invitationID, String username, UUID companyId) {
        Company company = companyRepository.findByID(companyId).get();

        if (company == null)
            throw new IllegalArgumentException("Company not found");

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        synchronized (company) {
            user.rejectCompanyInvitation(invitationID);
            userRepository.add(user);
        }
    }

    public List<InvitationResponse> getUserInvitations(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return user.getCompanyInvitations().stream()
            .map(invitation -> toInvitationResponse(invitation, userEmail))
                .toList();
    }

        private InvitationResponse toInvitationResponse(Invitation invitation, String userEmail) {
        Company company = companyRepository.findByID(invitation.getCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        if (invitation instanceof ManagerInvitation managerInvitation) {
            return new InvitationResponse(
                    invitation.getId(),
                    invitation.getCompanyId(),
                company.getName(),
                    invitation.getAppointerUser().getEmail(),
                    userEmail,
                    "MANAGER",
                    managerInvitation.getPremissions());
        }

        if (invitation instanceof OwnerInvitation) {
            return new InvitationResponse(
                    invitation.getId(),
                    invitation.getCompanyId(),
                company.getName(),
                    invitation.getAppointerUser().getEmail(),
                    userEmail,
                    "OWNER",
                    null);
        }

        throw new IllegalStateException("Unknown invitation type");
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
            userRepository.add(managerUser);
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

    public String getCompanyHierarchyMermaid(UUID companyId, String requesterEmail) {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User requesterUser = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        return requesterUser.getHierarchyMermaid(companyId);
    }

    public List<CompanyMembershipResponse> getUserCompanies(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        List<UUID> companyIds = userRepository.getCompaniesIdsByMember(userEmail);
        return companyIds.stream()
                .map(companyId -> {
                    Company company = companyRepository.findByID(companyId).get();
                    ICompanyMember userRole = userRepository.findByEmail(userEmail).get().getCompanyRole(companyId);
                    return new CompanyMembershipResponse(
                            company.getId(),
                            company.getName(),
                            userRole.getRoleName(),
                            company.getStatus().toString()
                    );
                })
                .toList();
    }

    public CompanyAccessResponse getCompanyAccess(UUID companyId, String userEmail) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ICompanyMember role = user.getCompanyRole(companyId);
        if (role == null) {
            throw new IllegalArgumentException("User is not a member of the company");
        }

        List<CompanyPermission> grantedPermissions = Arrays.stream(CompanyPermission.values())
                .filter(permission -> hasCompanyPermission(role, permission))
                .toList();

        return new CompanyAccessResponse(
                company.getId(),
                company.getName(),
                userEmail,
                role.getRoleName(),
                company.getStatus().name(),
                grantedPermissions);
    }

    /**
     * Return the list of usernames (emails) of the owner and all subordinates
     * in the company hierarchy. Throws when caller is not an owner/founder.
     */
    public List<String> getOwnerAndSubordinatesUsernames(UUID companyId, String ownerEmail) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Owner email is required");
        }

        User ownerUser = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Owner user not found"));

        ICompanyMember role = ownerUser.getCompanyRole(companyId);
        if (!(role instanceof CompanyOwner) && !(role instanceof CompanyFounder)) {
            throw new IllegalArgumentException("The caller is not a company owner or founder");
        }

        // The owner/report edges are owned by the persisted company_members table
        // (appointer_username); the in-memory subordinate graph is not rehydrated
        // under the JPA profile, so derive the set from the repository.
        return userRepository.getOwnerAndSubordinatesUsernames(companyId, role.getUsername());
    }

    private boolean hasCompanyPermission(ICompanyMember role, CompanyPermission permission) {
        if (role instanceof CompanyFounder || role instanceof CompanyOwner) {
            return true;
        }

        if (role instanceof CompanyManager manager) {
            return manager.getPremissions().contains(permission);
        }

        return false;
    }
}
