package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserRepository implements IUserRepository {
    private final Map<UUID, User> users = new HashMap<>();

    @Override
    public void add(User user) {
        users.put(user.getId(), user);
    }

    @Override
    public Optional<User> getUser(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean exists(int userId) {
        return users.containsKey(userId);
    }
}