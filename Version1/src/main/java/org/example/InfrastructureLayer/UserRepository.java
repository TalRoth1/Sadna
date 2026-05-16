package org.example.InfrastructureLayer;

import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserRepository implements IUserRepository {
    private final Map<UUID, User> users = new HashMap<>();
    private final Map<UUID, Admin> admins = new HashMap<>();

    @Override
    public void add(User user) {
        users.put(user.getId(), user);
    }

    @Override
    public Optional<User> getUser(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean exists(UUID userId) {
        return users.containsKey(userId);
    }

    @Override
    public boolean isSystemAdmin(String username) {
        return admins.values().stream()
                .anyMatch(admin -> admin.getUsername().equals(username));
    }

    @Override
    public boolean existsByEmail(String email)
    {
        if (email == null) return false;
        
        for (Map.Entry<UUID, User> entry : users.entrySet()) {
            if (email.equals(entry.getValue().getEmail())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<User> findByEmail(String email)
    {
        if (email == null) return null;
        
        for (Map.Entry<UUID, User> entry : users.entrySet()) {
            if (email.equals(entry.getValue().getEmail())) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return null;
    }

    @Override
    public List<UUID> getCompaniesIdsByMember(String username) {
        User user = findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getCompanyRoles().entrySet().stream()
                .filter(entry -> entry.getValue() != null) // Filter out entries with null roles
                .map(Map.Entry::getKey) // Extract the company IDs
                .toList();
    }

    @Override
    public boolean isCompanyOwner(String username, UUID companyId) {
        User user = findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.isOwnerInCompany(companyId);
    }

    @Override
    public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId) {
        User user = findByEmail(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.hasPremisions(companyId, permission, eventId);
    }
    

    public void addAdmin(Admin admin) {
        admins.put(admin.getId(), admin);
    }

    public boolean existsAdmin(UUID adminId) {
        return admins.containsKey(adminId);
    }
}