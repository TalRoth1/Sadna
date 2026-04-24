package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

import java.util.Optional;

public interface IUserRepository {
    void add(User user);

    Optional<User> getById(int userId);

    boolean exists(int userId);
}