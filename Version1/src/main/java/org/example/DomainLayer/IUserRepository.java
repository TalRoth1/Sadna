package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.UserAggregate.User;

public interface IUserRepository {
    public User getUser(UUID UID);
}
