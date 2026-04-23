package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

public interface IUserRepository {
    User findByID(String userID);
}
