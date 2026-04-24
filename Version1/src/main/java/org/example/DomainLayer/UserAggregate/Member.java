package org.example.DomainLayer.UserAggregate;

public class Member extends User {
    private boolean loggedIn;

    public Member(int id) {
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