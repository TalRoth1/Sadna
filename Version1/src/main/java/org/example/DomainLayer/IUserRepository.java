package org.example.DomainLayer;

import org.example.DomainLayer.UserAggregate.User;

public interface IUserRepository {
    User findById(String id); // מיוז קייס 1
    User findByEmail(String email); // מיוז קייס 3
    void save(User user); // מיוז קייס 2
    boolean existsByEmail(String email); // מיוז קייס 2 (בדיקת תפוס)
}