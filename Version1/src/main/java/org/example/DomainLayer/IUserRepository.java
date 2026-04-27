package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

public interface IUserRepository {
    public User getUser(String UID);
}
