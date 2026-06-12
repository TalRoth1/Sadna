package org.example.DomainLayer;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public boolean isCompanyOwner(String username, UUID companyId);

    public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId);

    public Map<UUID, User> getAllUsers();

    void addAdmin(Admin adminImpl);
}
