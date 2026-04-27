package org.example.DomainLayer.Repositories;

import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.UserAggregate.User;

import java.util.HashMap;
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
}