package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

import java.util.Optional;
import java.util.UUID;

public interface IUserRepository {
    void add(User user);

    public Optional<User> getUser(UUID UID);

    boolean exists(int userId);
}
