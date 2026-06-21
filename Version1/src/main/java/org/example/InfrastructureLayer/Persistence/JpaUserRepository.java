package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile("localdb")
@Transactional
public class JpaUserRepository implements IUserRepository {

    private final SpringDataUserRepository userJpa;
    private final SpringDataAdminRepository adminJpa;
    private final SpringDataCompanyMemberRepository companyMemberJpa;
    private final SpringDataInvitationRepository invitationJpa;

    public JpaUserRepository(SpringDataUserRepository userJpa,
                             SpringDataAdminRepository adminJpa,
                             SpringDataCompanyMemberRepository companyMemberJpa,
                             SpringDataInvitationRepository invitationJpa) {
        this.userJpa = userJpa;
        this.adminJpa = adminJpa;
        this.companyMemberJpa = companyMemberJpa;
        this.invitationJpa = invitationJpa;
    }

    @Override
    public void add(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        String identifier = extractUserIdentifier(user);
        if (identifier == null) {
            identifier = user.getId().toString();
        }

        Optional<UserEntity> existing = userJpa.findByUsername(identifier);

        UserEntity entityToSave;

        if (existing.isPresent()) {
            UserEntity existingEntity = existing.get();

            entityToSave = new UserEntity(
                    existingEntity.getId(),
                    identifier,
                    user.getPasswordHash(),
                    user.getStatus(),
                    existingEntity.getCreatedAt(),
                    existingEntity.getUpdatedAt()
            );
        } else {
            entityToSave = toEntity(user);
        }

        userJpa.save(entityToSave);

        List<String> identifiers = identifiersForUser(user);
        if (!identifiers.contains(identifier)) {
            identifiers = new ArrayList<>(identifiers);
            identifiers.add(identifier);
        }

        companyMemberJpa.deleteByIdUsernameIn(identifiers);
        invitationJpa.deleteByApointeeUsernameIn(identifiers);

        saveCompanyRoles(user);
        saveInvitations(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUser(UUID UID) {
        if (UID == null) {
            return Optional.empty();
        }

        return userJpa.findById(UID)
                .map(entity -> toDomain(entity, true));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return userId != null && userJpa.existsById(userId);
    }

    /*
     * ERD has no email column.
     * In the DB schema, users.username is the persistent login identifier.
     * Therefore, email lookups are mapped to username.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        String identifier = normalizeIdentifier(email);
        return identifier != null && userJpa.existsByUsername(identifier);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        String identifier = normalizeIdentifier(username);
        return identifier != null && userJpa.existsByUsername(identifier);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String identifier = normalizeIdentifier(email);

        if (identifier == null) {
            return Optional.empty();
        }

        return userJpa.findByUsername(identifier)
                .map(entity -> toDomain(entity, true));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, User> getAllUsers() {
        return userJpa.findAll()
                .stream()
                .map(entity -> toDomain(entity, false))
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSystemAdmin(String username) {
        String identifier = normalizeIdentifier(username);
        return identifier != null && adminJpa.existsByUsername(identifier);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsAdmin(UUID adminId) {
        return adminId != null && adminJpa.existsById(adminId);
    }

    @Override
    public void addAdmin(Admin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("Admin is required");
        }

        String username = normalizeIdentifier(admin.getUsername());

        if (username == null) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (admin.getId() == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }

        if (adminJpa.existsByUsername(username)) {
            return;
        }

        /*
         * According to the original ERD/schema, admins.username refers to an
         * existing users.username. So the admin user must be seeded as a User
         * before we insert into admins.
         */
        if (!userJpa.existsByUsername(username)) {
            throw new IllegalStateException(
                    "Cannot create admin because no user exists with username: " + username
            );
        }

        adminJpa.save(new AdminEntity(admin.getId(), username));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getCompaniesIdsByMember(String username) {
        List<String> identifiers = identifiersForIdentifier(username);

        return companyMemberJpa.findByIdUsernameIn(identifiers)
                .stream()
                .map(CompanyMemberEntity::getCompanyId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getOwnerAndSubordinatesUsernames(UUID companyId, String ownerUsername) {
        String owner = normalizeIdentifier(ownerUsername);

        if (companyId == null || owner == null) {
            return List.of();
        }

        // Build the appointer -> direct reports adjacency from the persisted
        // company_members rows, then BFS down from the owner.
        Map<String, List<String>> reportsByAppointer = new HashMap<>();
        for (CompanyMemberEntity member : companyMemberJpa.findByIdCompanyId(companyId)) {
            String appointer = normalizeIdentifier(member.getAppointerUsername());
            String memberUsername = normalizeIdentifier(member.getUsername());

            if (appointer == null || memberUsername == null) {
                continue;
            }

            reportsByAppointer.computeIfAbsent(appointer, key -> new ArrayList<>())
                    .add(memberUsername);
        }

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(owner);
        visited.add(owner);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);

            for (String report : reportsByAppointer.getOrDefault(current, List.of())) {
                if (visited.add(report)) {
                    queue.add(report);
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompanyOwner(String username, UUID companyId) {
        if (companyId == null) {
            return false;
        }

        return companyMemberJpa
                .findFirstByIdUsernameInAndIdCompanyId(
                        identifiersForIdentifier(username),
                        companyId
                )
                .map(member ->
                        "FOUNDER".equalsIgnoreCase(member.getRole())
                                || "OWNER".equalsIgnoreCase(member.getRole()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(String username,
                                 UUID companyId,
                                 CompanyPermission permission,
                                 UUID eventId) {
        if (companyId == null || permission == null) {
            return false;
        }

        Optional<CompanyMemberEntity> memberOpt =
                companyMemberJpa.findFirstByIdUsernameInAndIdCompanyId(
                        identifiersForIdentifier(username),
                        companyId
                );

        if (memberOpt.isEmpty()) {
            return false;
        }

        CompanyMemberEntity member = memberOpt.get();

        if ("FOUNDER".equalsIgnoreCase(member.getRole())
                || "OWNER".equalsIgnoreCase(member.getRole())) {
            return true;
        }

        if (!"MANAGER".equalsIgnoreCase(member.getRole())) {
            return false;
        }

        return member.getPermissions().contains(permission);
    }

    private UserEntity toEntity(User user) {
        String identifier = extractUserIdentifier(user);

        if (identifier == null) {
            identifier = user.getId().toString();
        }

        return new UserEntity(
                user.getId(),
                identifier,
                user.getPasswordHash(),
                user.getStatus()
        );
    }

    private User toDomain(UserEntity entity) {
        return toDomain(entity, true);
    }

    private User toDomain(UserEntity entity, boolean includeInvitations) {
        User user = new User(
                entity.getId(),
                entity.getUsername(),
                entity.getUsername(),
                entity.getPasswordHash(),
                UserRole.MEMBER,
                entity.getStatus(),
                0
        );

        restoreCompanyRoles(user);

        if (includeInvitations) {
            restoreInvitations(user);
        }

        return user;
    }

    private void restoreCompanyRoles(User user) {
        List<String> userIdentifiers = identifiersForUser(user);

        Set<String> normalizedUserIdentifiers = new HashSet<>();
        for (String identifier : userIdentifiers) {
            String normalized = normalizeIdentifier(identifier);
            if (normalized != null) {
                normalizedUserIdentifiers.add(normalized);
            }
        }

        // Determine which companies this user belongs to.
        Set<UUID> companyIds = new LinkedHashSet<>();
        for (CompanyMemberEntity member : companyMemberJpa.findByIdUsernameIn(userIdentifiers)) {
            companyIds.add(member.getCompanyId());
        }

        // For each company, rebuild the full appointer -> subordinate tree so the
        // in-memory hierarchy (subordinates list + appointer chain) is restored.
        // Without this the hierarchy view shows only the founder, subordinate
        // checks fail, and re-saving a member wipes its appointer link.
        for (UUID companyId : companyIds) {
            ICompanyMember ownNode = buildCompanyHierarchy(companyId, normalizedUserIdentifiers);
            if (ownNode != null) {
                user.getCompanyRoles().put(companyId, ownNode);
            }
        }
    }

    /**
     * Rebuilds the full company hierarchy from the persisted company_members rows
     * and returns the node that belongs to the user being rehydrated.
     *
     * Edges are wired from appointer_username. Non-founder members whose appointer
     * link is missing (legacy rows persisted before appointer links were restored
     * on load) are attached directly under the founder so they still appear in the
     * hierarchy and pass owner/subordinate permission checks.
     */
    private ICompanyMember buildCompanyHierarchy(UUID companyId, Set<String> targetUsernames) {
        List<CompanyMemberEntity> members = companyMemberJpa.findByIdCompanyId(companyId);

        Map<String, ICompanyMember> nodes = new HashMap<>();
        CompanyFounder founder = null;

        for (CompanyMemberEntity member : members) {
            ICompanyMember node = toCompanyMemberDomain(member);
            String key = normalizeIdentifier(member.getUsername());

            if (node == null || key == null) {
                continue;
            }

            nodes.put(key, node);

            if (node instanceof CompanyFounder companyFounder) {
                founder = companyFounder;
            }
        }

        for (CompanyMemberEntity member : members) {
            String key = normalizeIdentifier(member.getUsername());
            ICompanyMember node = key == null ? null : nodes.get(key);

            if (node == null || node instanceof CompanyFounder) {
                continue;
            }

            String appointerKey = normalizeIdentifier(member.getAppointerUsername());
            ICompanyMember appointerNode = appointerKey == null ? null : nodes.get(appointerKey);

            if (!(appointerNode instanceof CompanyOwner) && founder != null) {
                appointerNode = founder;
            }

            if (appointerNode instanceof CompanyOwner owner && appointerNode != node) {
                owner.addSubordinate(node);
            }
        }

        for (Map.Entry<String, ICompanyMember> entry : nodes.entrySet()) {
            if (targetUsernames.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private ICompanyMember toCompanyMemberDomain(CompanyMemberEntity entity) {
        String role = entity.getRole();

        if ("FOUNDER".equalsIgnoreCase(role)) {
            return new CompanyFounder(entity.getUsername());
        }

        if ("OWNER".equalsIgnoreCase(role)) {
            return new CompanyOwner(entity.getUsername(), null);
        }

        if ("MANAGER".equalsIgnoreCase(role)) {
            return new CompanyManager(entity.getUsername(), null, entity.getPermissions());
        }

        return null;
    }

    private void restoreInvitations(User appointee) {
        List<String> appointeeIdentifiers = identifiersForUser(appointee);

        for (InvitationEntity entity : invitationJpa.findByApointeeUsernameIn(appointeeIdentifiers)) {
            Optional<User> appointerOpt = findUserWithoutInvitations(entity.getAppointerUsername());

            if (appointerOpt.isEmpty()) {
                continue;
            }

            User appointer = appointerOpt.get();

            Invitation invitation;

            if ("MANAGER".equalsIgnoreCase(entity.getRole())) {
                invitation = new ManagerInvitation(
                        entity.getId(),
                        appointer,
                        appointee,
                        entity.getCompanyId(),
                        entity.getPermissions()
                );
            } else if ("OWNER".equalsIgnoreCase(entity.getRole())) {
                invitation = new OwnerInvitation(
                        entity.getId(),
                        appointer,
                        appointee,
                        entity.getCompanyId()
                );
            } else {
                continue;
            }

            appointee.restoreCompanyInvitation(invitation);
        }
    }

    private Optional<User> findUserWithoutInvitations(String identifier) {
        String normalized = normalizeIdentifier(identifier);

        if (normalized == null) {
            return Optional.empty();
        }

        return userJpa.findByUsername(normalized)
                .map(entity -> toDomain(entity, false));
    }

    private void saveCompanyRoles(User user) {
        for (Map.Entry<UUID, ICompanyMember> entry : user.getCompanyRoles().entrySet()) {
            UUID companyId = entry.getKey();
            ICompanyMember role = entry.getValue();

            if (companyId == null || role == null) {
                continue;
            }

            String roleUsername = normalizeIdentifier(role.getUsername());
            if (roleUsername == null) {
                continue;
            }

            String roleName = normalizeRole(role.getRoleName());

            String appointerUsername = null;
            if (role.getAppointer() != null) {
                appointerUsername = normalizeIdentifier(role.getAppointer().getUsername());
            }

            Set<CompanyPermission> permissions = new HashSet<>();

            if (role instanceof CompanyManager manager) {
                permissions.addAll(manager.getPremissions());
            }

            CompanyMemberEntity entity = new CompanyMemberEntity(
                    companyId,
                    roleUsername,
                    roleName,
                    appointerUsername,
                    permissions
            );

            companyMemberJpa.save(entity);
        }
    }

    private void saveInvitations(User user) {
        for (Invitation invitation : user.getCompanyInvitations()) {
            String role;
            Set<CompanyPermission> permissions = new HashSet<>();

            if (invitation instanceof ManagerInvitation managerInvitation) {
                role = "MANAGER";
                permissions.addAll(managerInvitation.getPremissions());
            } else if (invitation instanceof OwnerInvitation) {
                role = "OWNER";
            } else {
                continue;
            }

            String appointerUsername = extractUserIdentifier(invitation.getAppointerUser());
            String apointeeUsername = extractUserIdentifier(invitation.getAppointeeUser());

            if (appointerUsername == null || apointeeUsername == null) {
                continue;
            }

            InvitationEntity entity = new InvitationEntity(
                    invitation.getId(),
                    invitation.getCompanyId(),
                    appointerUsername,
                    apointeeUsername,
                    role,
                    permissions
            );

            invitationJpa.save(entity);
        }
    }

    private String extractUserIdentifier(User user) {
        if (user == null) {
            return null;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return normalizeIdentifier(user.getEmail());
        }

        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return normalizeIdentifier(user.getUsername());
        }

        return null;
    }

    private List<String> identifiersForUser(User user) {
        if (user == null) {
            return List.of();
        }

        Set<String> identifiers = new LinkedHashSet<>();

        String email = normalizeIdentifier(user.getEmail());
        if (email != null) {
            identifiers.add(email);
        }

        String username = normalizeIdentifier(user.getUsername());
        if (username != null) {
            identifiers.add(username);
        }

        if (identifiers.isEmpty() && user.getId() != null) {
            identifiers.add(user.getId().toString());
        }

        return new ArrayList<>(identifiers);
    }

    private List<String> identifiersForIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier);

        if (normalized == null) {
            return List.of();
        }

        return List.of(normalized);
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }

    private String normalizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }

        String normalized = roleName.trim().toUpperCase();

        if ("FOUNDER".equals(normalized)
                || "OWNER".equals(normalized)
                || "MANAGER".equals(normalized)) {
            return normalized;
        }

        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getAllAdminUsernames() {
        return adminJpa.findAll().stream()
                .map(AdminEntity::getUsername)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countCompanyMembersByRole(UUID companyId) {
        return companyMemberJpa.findByIdCompanyId(companyId).stream()
                .collect(Collectors.groupingBy(
                        e -> normalizeRole(e.getRole()),
                        Collectors.counting()));
    }
}