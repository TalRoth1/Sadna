package org.example.DomainLayer;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.UserAggregate.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserRepository implements IUserRepository {
    private final Map<Integer, User> users = new HashMap<>();
    private final Map<Integer, Admin> admins = new HashMap<>();

    @Override
    public void add(User user) {
        users.put(user.getId(), user);
    }

    @Override
    public Optional<User> getById(int userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean exists(int userId) {
        return users.containsKey(userId);
    }

    @Override
    public boolean isSystemAdmin(String username) {
        return admins.values().stream()
                .anyMatch(admin -> admin.getUsername().equals(username));
    }
}