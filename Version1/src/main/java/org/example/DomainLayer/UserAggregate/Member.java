package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

public class Member extends User {
    private boolean loggedIn;

    public Member(UUID id) {
        super(id);
        this.loggedIn = false;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void login() {
        this.loggedIn = true;
    }

    public void logout() {
        this.loggedIn = false;
    }
}