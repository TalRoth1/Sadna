package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Repository
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

        userJpa.save(toEntity(user));

        List<String> identifiers = identifiersForUser(user);

        companyMemberJpa.deleteByUsernameIn(identifiers);
        invitationJpa.deleteByAppointeeUsernameIn(identifiers);

        saveCompanyRoles(user);
        saveInvitations(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUser(UUID UID) {
        if (UID == null) {
            return Optional.empty();
        }

        return userJpa.findById(UID).map(entity -> toDomain(entity, true));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID userId) {
        return userId != null && userJpa.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSystemAdmin(String username) {
        String normalized = normalizeIdentifier(username);
        if (normalized == null) {
            return false;
        }

        return adminJpa.existsByUsername(normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null && userJpa.existsByEmail(normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        String normalized = normalizeIdentifier(username);
        return normalized != null && userJpa.existsByUsername(normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return Optional.empty();
        }

        return userJpa.findByEmail(normalized).map(entity -> toDomain(entity, true));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsAdmin(UUID adminId) {
        return adminId != null && adminJpa.existsById(adminId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getCompaniesIdsByMember(String username) {
        List<String> identifiers = identifiersForIdentifier(username);

        return companyMemberJpa.findByUsernameIn(identifiers)
                .stream()
                .map(CompanyMemberEntity::getCompanyId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompanyOwner(String username, UUID companyId) {
        if (companyId == null) {
            return false;
        }

        return companyMemberJpa
                .findFirstByUsernameInAndCompanyId(identifiersForIdentifier(username), companyId)
                .map(member -> member.getRole().equals("FOUNDER") || member.getRole().equals("OWNER"))
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
                companyMemberJpa.findFirstByUsernameInAndCompanyId(
                        identifiersForIdentifier(username),
                        companyId
                );

        if (memberOpt.isEmpty()) {
            return false;
        }

        CompanyMemberEntity member = memberOpt.get();

        if (member.getRole().equals("FOUNDER") || member.getRole().equals("OWNER")) {
            return true;
        }

        if (!member.getRole().equals("MANAGER")) {
            return false;
        }

        boolean hasPermission = member.getPermissions().contains(permission);

        if (!hasPermission) {
            return false;
        }

        if (eventId == null) {
            return true;
        }

        return member.getEventIds().isEmpty() || member.getEventIds().contains(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, User> getAllUsers() {
        return userJpa.findAll()
                .stream()
                .map(entity -> toDomain(entity, true))
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(
                user.getId(),
                normalizeIdentifier(user.getUsername()),
                normalizeEmail(user.getEmail()),
                user.getPasswordHash(),
                user.getRole(),
                user.getStatus(),
                user.getAge()
        );
    }

    private User toDomain(UserEntity entity, boolean includeInvitations) {
        User user = new User(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getStatus(),
                entity.getAge()
        );

        restoreCompanyRoles(user);

        if (includeInvitations) {
            restoreInvitations(user);
        }

        return user;
    }

    private void restoreCompanyRoles(User user) {
        for (CompanyMemberEntity member : companyMemberJpa.findByUsernameIn(identifiersForUser(user))) {
            ICompanyMember role = toCompanyMemberDomain(member);
            if (role != null) {
                role.getEventsIds().addAll(member.getEventIds());
                user.getCompanyRoles().put(member.getCompanyId(), role);
            }
        }
    }

    private ICompanyMember toCompanyMemberDomain(CompanyMemberEntity entity) {
        return switch (entity.getRole()) {
            case "FOUNDER" -> new CompanyFounder(entity.getUsername());
            case "OWNER" -> new CompanyOwner(entity.getUsername(), null);
            case "MANAGER" -> new CompanyManager(entity.getUsername(), null, entity.getPermissions());
            default -> null;
        };
    }

    private void restoreInvitations(User appointee) {
        List<String> appointeeIdentifiers = identifiersForUser(appointee);

        for (InvitationEntity entity : invitationJpa.findByAppointeeUsernameIn(appointeeIdentifiers)) {
            Optional<User> appointerOpt = findUserWithoutInvitations(entity.getAppointerUsername());

            if (appointerOpt.isEmpty()) {
                continue;
            }

            User appointer = appointerOpt.get();

            Invitation invitation;

            if ("MANAGER".equals(entity.getType())) {
                invitation = new ManagerInvitation(
                        entity.getId(),
                        appointer,
                        appointee,
                        entity.getCompanyId(),
                        entity.getPermissions()
                );
            } else if ("OWNER".equals(entity.getType())) {
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
        Optional<UserEntity> entityOpt = findEntityByIdentifier(identifier);

        return entityOpt.map(entity -> toDomain(entity, false));
    }

    private Optional<UserEntity> findEntityByIdentifier(String identifier) {
        String email = normalizeEmail(identifier);
        if (email != null) {
            Optional<UserEntity> byEmail = userJpa.findByEmail(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        String username = normalizeIdentifier(identifier);
        if (username != null) {
            return userJpa.findByUsername(username);
        }

        return Optional.empty();
    }

    private void saveCompanyRoles(User user) {
        for (Map.Entry<UUID, ICompanyMember> entry : user.getCompanyRoles().entrySet()) {
            UUID companyId = entry.getKey();
            ICompanyMember role = entry.getValue();

            if (companyId == null || role == null) {
                continue;
            }

            String roleName = role.getRoleName().toUpperCase();

            String appointerUsername = null;
            if (role.getAppointer() != null) {
                appointerUsername = role.getAppointer().getUsername();
            }

            Set<CompanyPermission> permissions = new HashSet<>();

            if (role instanceof CompanyManager manager) {
                permissions.addAll(manager.getPremissions());
            }

            Set<UUID> eventIds = new HashSet<>(role.getEventsIds());

            CompanyMemberEntity entity = new CompanyMemberEntity(
                    companyId,
                    role.getUsername(),
                    roleName,
                    appointerUsername,
                    permissions,
                    eventIds
            );

            companyMemberJpa.save(entity);
        }
    }

    private void saveInvitations(User user) {
        for (Invitation invitation : user.getCompanyInvitations()) {
            String type;
            Set<CompanyPermission> permissions = new HashSet<>();

            if (invitation instanceof ManagerInvitation managerInvitation) {
                type = "MANAGER";
                permissions.addAll(managerInvitation.getPremissions());
            } else if (invitation instanceof OwnerInvitation) {
                type = "OWNER";
            } else {
                continue;
            }

            String appointerUsername = extractUserIdentifier(invitation.getAppointerUser());
            String appointeeUsername = extractUserIdentifier(invitation.getAppointeeUser());

            InvitationEntity entity = new InvitationEntity(
                    invitation.getId(),
                    invitation.getCompanyId(),
                    appointerUsername,
                    appointeeUsername,
                    type,
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
            return normalizeEmail(user.getEmail());
        }

        return normalizeIdentifier(user.getUsername());
    }

    private List<String> identifiersForUser(User user) {
        if (user == null) {
            return List.of();
        }

        Set<String> identifiers = new LinkedHashSet<>();

        String email = normalizeEmail(user.getEmail());
        if (email != null) {
            identifiers.add(email);
        }

        String username = normalizeIdentifier(user.getUsername());
        if (username != null) {
            identifiers.add(username);
        }

        return new ArrayList<>(identifiers);
    }

    private List<String> identifiersForIdentifier(String identifier) {
        Set<String> identifiers = new LinkedHashSet<>();

        String email = normalizeEmail(identifier);
        if (email != null) {
            identifiers.add(email);
        }

        String username = normalizeIdentifier(identifier);
        if (username != null) {
            identifiers.add(username);
        }

        return new ArrayList<>(identifiers);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim().toLowerCase();
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}