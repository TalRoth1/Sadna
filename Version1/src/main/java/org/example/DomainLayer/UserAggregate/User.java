package org.example.DomainLayer.UserAggregate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

// --- Aggregate Root: User ---
public class User {
    private UUID id;
    private String username;
    private String email; // identifier
    private String passwordHash;
    private UserRole role;
    private UserStatus status;
    private float age;
    private HashMap<UUID, ICompanyMember> companyRoles;
    private final Map<UUID, Invitation> CompanyInvitations;

    public User(UUID id, String username, String email, String passwordHash, float age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.MEMBER;
        this.status = UserStatus.NOT_LOGGED_IN;
        this.age = age;
        this.companyRoles = new HashMap<>();
        this.CompanyInvitations = new HashMap<>();
    }

    /**
     * Guest constructor.
     *
     * Creates an anonymous visitor with role=GUEST and status=NOT_LOGGED_IN.
     * Guests have no email, no password, and a generated placeholder username.
     * They exist only to give the system a UUID to attach transient state to
     * (cart, active purchase, etc.) until/unless they register or log in.
     */
    public User(UUID id) {
        this.id = id;
        this.username = "guest-" + id.toString().substring(0, 8);
        this.email = null;
        this.passwordHash = null;
        this.role = UserRole.GUEST;
        this.status = UserStatus.NOT_LOGGED_IN;
        this.age = 0;
        this.companyRoles = new HashMap<>();
        this.CompanyInvitations = new HashMap<>();
    }

    public void login() {
        if (this.status == UserStatus.LOGGED_IN) {
            throw new IllegalStateException("The user is already logged in.");
        }
        this.status = UserStatus.LOGGED_IN;
        this.role = UserRole.MEMBER;
    }

    public void logout() {
        if (this.status == UserStatus.NOT_LOGGED_IN) {
            throw new IllegalStateException("The user is already logged out.");
        }
        this.status = UserStatus.NOT_LOGGED_IN;
        this.role = UserRole.GUEST;
    }

    // company role management
    public boolean isCompanyMember(UUID companyId) {
        return companyRoles.containsKey(companyId);
    }

    public UUID inviteUserToBecomeOwner(UUID companyId, User appointerUser) {
        if (!appointerUser.isCompanyMember(companyId) || !appointerUser.isOwnerInCompany(companyId)) {
            throw new IllegalArgumentException(
                    "The appointer is not a company owner and therefore cannot invite a new owner");
        }
        if (isCompanyMember(companyId)) {
            ICompanyMember currentRole = companyRoles.get(companyId);
            if (currentRole instanceof CompanyOwner) {
                throw new IllegalStateException("Failed: The user is already an owner in the company.");
            }
            if (!currentRole.isSubordinateOf(appointerUser.getCompanyRole(companyId).getUsername())) {
                throw new IllegalArgumentException(
                        "The appointee is a manager but is not a subordinate of the appointer and therefore cannot be appointed as an owner by him/her");
            }
        }
        Invitation OwnerInvitation = new OwnerInvitation(appointerUser, this, companyId);
        CompanyInvitations.put(OwnerInvitation.getId(), OwnerInvitation);
        return OwnerInvitation.getId();
    }

    public UUID inviteUserToBecomeManager(UUID companyId, User appointerUser, Set<CompanyPermission> permissions) {
        if (!appointerUser.isCompanyMember(companyId) || !appointerUser.isOwnerInCompany(companyId)) {
            throw new IllegalArgumentException(
                    "The appointer is not a company owner and therefore cannot invite a new owner");
        }
        // Prevent inviting an existing company member; only non-members may be invited
        if (isCompanyMember(companyId)) {
            throw new IllegalArgumentException("The appointee is already a member of the company and therefore cannot be invited as a manager");
        }
        Invitation ManagerInvitation = new ManagerInvitation(appointerUser, this, companyId, permissions);
        CompanyInvitations.put(ManagerInvitation.getId(), ManagerInvitation);
        return ManagerInvitation.getId();
    }

    public void acceptCompanyInvitation(UUID invitationId) {
        if (!CompanyInvitations.containsKey(invitationId)) {
            throw new IllegalStateException("Invalid invitation ID.");
        }
        Invitation invitation = CompanyInvitations.get(invitationId);
        if (invitation instanceof OwnerInvitation OwnerInvitation) {
            becomeOwner(OwnerInvitation.getCompanyId(), OwnerInvitation.getAppointerUser()); // appointerUser is not
            // needed for becoming
            // owner
        } else if (invitation instanceof ManagerInvitation ManagerInvitation) {
            becomeManager(ManagerInvitation.getCompanyId(), ManagerInvitation.getAppointerUser(),
                    ManagerInvitation.getPremissions()); // appointerUser is not needed for becoming manager
        } else {
            throw new IllegalStateException("Unknown invitation type.");
        }
        CompanyInvitations.remove(invitationId);
    }

    public void rejectCompanyInvitation(UUID invitationId) {
        if (!CompanyInvitations.containsKey(invitationId)) {
            throw new IllegalStateException("Invalid invitation ID.");
        }
        CompanyInvitations.remove(invitationId);
    }

    private void becomeOwner(UUID companyId, User appointerUser) {
        if (!appointerUser.isCompanyMember(companyId)
                || !appointerUser.isOwnerInCompany(companyId)) {
            throw new IllegalStateException(
                    "Failed: Appointer user must be an owner or founder of the company at the time of the appointment.");
        }
        CompanyOwner appointerRole = (CompanyOwner) appointerUser.getCompanyRole(companyId);
        if (isCompanyMember(companyId)) {
            // case of an existing manager being appointed as an owner by his current
            // appointer or by another owner/founder that is above him in the hierarchy
            ICompanyMember currentRole = companyRoles.get(companyId);
            if (currentRole instanceof CompanyOwner) {
                throw new IllegalStateException("Failed: The user is already an owner in the company.");
            }
            if (!currentRole.isSubordinateOf(appointerRole.getUsername())) {
                throw new IllegalArgumentException(
                        "The appointee is a manager but is not a subordinate of the appointer and therefore cannot be appointed as an owner by him/her");
            }
            CompanyOwner appointeeOldAppointer = (CompanyOwner) currentRole.getAppointer();
            // remove the appointee from his current appointer's subordinates and set his
            // appointer to null before creating his new role
            appointeeOldAppointer.removeSubordinate(appointeeOldAppointer);
            currentRole.setAppointer(null);
            companyRoles.remove(companyId);
            // create the new owner role and add it to the company roles and to the
            // appointer's subordinates
            CompanyOwner newOwnerRole = new CompanyOwner(username, appointerRole);
            companyRoles.put(companyId, newOwnerRole);
            appointerRole.addSubordinate(newOwnerRole);
        }
        // case of a new owner with no previous role in the company being appointed as
        // an owner by an existing owner/founder
        else {
            CompanyOwner newOwnerRole = new CompanyOwner(username, appointerRole);
            companyRoles.put(companyId, newOwnerRole);
            appointerRole.addSubordinate(newOwnerRole);
        }
    }

    private void becomeManager(UUID companyId, User appointerUser, Set<CompanyPermission> permissions) {
        if (!appointerUser.isCompanyMember(companyId)
                || (!appointerUser.isOwnerInCompany(companyId) && !appointerUser.isFounderInCompany(companyId))) {
            throw new IllegalStateException(
                    "Failed: Appointer user must be an owner or founder of the company at the time of the appointment.");
        }
        if (isCompanyMember(companyId)) {
            throw new IllegalStateException("Failed: The user is already a member of the company.");
        }
        CompanyOwner appointerRole = (CompanyOwner) appointerUser.getCompanyRole(companyId);
        CompanyManager newManagerRole = new CompanyManager(username, appointerRole, permissions);
        companyRoles.put(companyId, newManagerRole);
        appointerRole.addSubordinate(newManagerRole);
    }

    // the current user is the manager whose permissions are being changed, and OwnerUser is the owner performing the change. this function is being called on the manager whose permissions are being changed in order to check that the owner performing the change has the authority to change his/her permissions (i.e. that he/she is a subordinate of the owner).
    public void changeManagerPermissionsAsOwner(UUID companyId, User OwnerUser,
                                                Set<CompanyPermission> newPermissions) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (OwnerUser == null) {
            throw new IllegalArgumentException("Owner user is required");
        }
        if (!this.isCompanyMember(companyId) || !(this.getCompanyRole(companyId) instanceof CompanyOwner)) {
            throw new IllegalArgumentException(
                    "The user changing the permissions is not a company owner and therefore cannot change manager permissions");
        }
        if (!OwnerUser.isCompanyMember(companyId)
                || !(OwnerUser.getCompanyRole(companyId) instanceof CompanyOwner)) {
            throw new IllegalArgumentException(
                    "The user whose permissions are being changed is not a company manager and therefore cannot have his/her permissions changed");
        }
        if (!this.getCompanyRole(companyId).isSubordinateOf(OwnerUser.getCompanyRole(companyId).getUsername())) {
            throw new IllegalArgumentException(
                    "The user whose permissions are being changed is not a subordinate of the user changing the permissions and therefore cannot have his/her permissions changed by him/her");
        }
        CompanyManager managerRole = (CompanyManager) this.getCompanyRole(companyId);
        managerRole.setNewPremissions(newPermissions);
    }

    public boolean hasPremisions(UUID companyId, CompanyPermission permission, UUID eventId) {
        if (!isCompanyMember(companyId)) {
            return false;
        }
        ICompanyMember role = companyRoles.get(companyId);
        return role.hasPremission(permission, eventId);
    }

    public boolean isOwnerInCompany(UUID companyId) {
        ICompanyMember role = companyRoles.get(companyId);
        return role instanceof CompanyOwner || role instanceof CompanyFounder;
    }

    public boolean isManagerInCompany(UUID companyId) {
        ICompanyMember role = companyRoles.get(companyId);
        return role instanceof CompanyManager;
    }

    public boolean isFounderInCompany(UUID companyId) {
        ICompanyMember role = companyRoles.get(companyId);
        return role instanceof CompanyFounder;
    }

    public CompanyFounder getMyCompanyFounder(UUID companyId) {
        if (!isCompanyMember(companyId)) {
            throw new IllegalArgumentException("User is not a member of the company");
        }
        return companyRoles.get(companyId).getFounder();
    }

    // assumes that the domain layer service is checking whether the call is made by
    // a real system admin.
    public void removeFromCompanyAsAdmin(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (!isCompanyMember(companyId)) {
            throw new IllegalArgumentException("User is not a company member");
        }
        ICompanyMember memberToRemove = companyRoles.get(companyId);
        memberToRemove.removeFromCompanyHyrarchy();
        companyRoles.remove(companyId);
    }

    // this function is being called on the removed user by OwnerUser who is the
    // owner performing the removal, and is being passed the user that is being
    // removed as a parameter in order to check that the owner has the authority to
    // remove him/her (i.e. that the removed user is a subordinate of the owner).
    public void removeFromCompanyAsOwner(UUID companyId, User OwnerUser) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (OwnerUser == null) {
            throw new IllegalArgumentException("Owner user is required");
        }
        if (!this.isCompanyMember(companyId)) {
            throw new IllegalArgumentException("User is not a company member");
        }
        if (!OwnerUser.isCompanyMember(companyId) || !(OwnerUser.getCompanyRole(companyId) instanceof CompanyOwner)) {
            throw new IllegalArgumentException(
                    "The user performing the removal is not a company owner and therefore cannot remove other members from the company");
        }
        if (!this.getCompanyRole(companyId).isSubordinateOf(OwnerUser.getCompanyRole(companyId).getUsername())) {
            throw new IllegalArgumentException(
                    "The user performing the removal is not above the removed user in the company hierarchy and therefore cannot remove him/her");
        }
        ICompanyMember memberToRemove = companyRoles.get(companyId);
        memberToRemove.removeFromCompanyHyrarchy();
        companyRoles.remove(companyId);
    }

    public List<UUID> getMyEventIdsOfCompany(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (!isCompanyMember(companyId)) {
            throw new IllegalArgumentException("User is not a company member");
        }
        if (!isOwnerInCompany(companyId)) {
            throw new IllegalArgumentException(
                    "User is not a company owner and therefore does not have any event ids associated with him/her");
        }
        CompanyOwner memberRole = (CompanyOwner) companyRoles.get(companyId);
        return memberRole.getEventsUnderMe();
    }

    public String getHierarchyMermaid(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (!isCompanyMember(companyId) || !(companyRoles.get(companyId) instanceof CompanyOwner)) {
            throw new IllegalArgumentException(
                    "Only company owners can view the company hierarchy");
        }
        CompanyFounder founder = companyRoles.get(companyId).getFounder();
        if (founder == null) {
            throw new IllegalStateException("Company founder is not found, cannot build hierarchy");
        }
        StringBuilder sb = new StringBuilder();
        founder.buildMermaid(sb);
        return sb.toString();
    }

    // Getters...
    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public float getAge() {
        return age;
    }

    public UserRole getRole() {
        return role;
    }

    public Map<UUID, ICompanyMember> getCompanyRoles() {
        return companyRoles;
    }

    public ICompanyMember getCompanyRole(UUID companyId) {
        return companyRoles.get(companyId);
    }
}