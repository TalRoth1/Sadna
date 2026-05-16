package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserRepository {
    void add(User user);

    public Optional<User> getUser(UUID UID);

    boolean exists(UUID userId);

    boolean isSystemAdmin(String username);

    public boolean existsByEmail(String email);

    public Optional<User> findByEmail(String email);

    boolean existsAdmin(UUID adminId);

    public List<UUID> getCompaniesIdsByMember(String username);

    public boolean isCompanyOwner(String username, UUID companyId);
}
