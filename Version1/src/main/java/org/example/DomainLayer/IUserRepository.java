package org.example.DomainLayer;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IUserRepository {
    void add(User user);

    public Optional<User> getUser(UUID UID);

    boolean exists(UUID userId);

    boolean isSystemAdmin(String username);

    public boolean existsByEmail(String email);

    public boolean existsByUsername(String username);

    public Optional<User> findByEmail(String email);

    boolean existsAdmin(UUID adminId);

    public List<UUID> getCompaniesIdsByMember(String username);

    /**
     * Return the owner's own username plus the usernames of all transitive
     * subordinates in the company hierarchy. Derived from the persisted
     * appointer relationship so it survives a DB-backed reload (the in-memory
     * subordinate graph is not rehydrated under the JPA profile).
     */
    List<String> getOwnerAndSubordinatesUsernames(UUID companyId, String ownerUsername);

    public boolean isCompanyOwner(String username, UUID companyId);

    public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId);

    public Map<UUID, User> getAllUsers();

    void addAdmin(Admin adminImpl);

    Set<String> getAllAdminUsernames();

    Map<String, Long> countCompanyMembersByRole(UUID companyId);
}
