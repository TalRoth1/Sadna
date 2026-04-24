package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

public interface IUserRepository {
    public static User getUser(String UID) {
        throw new UnsupportedOperationException("Unimplemented method 'getUser'");
    }
}
