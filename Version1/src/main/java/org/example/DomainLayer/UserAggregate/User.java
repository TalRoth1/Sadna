package org.example.DomainLayer.UserAggregate;

public abstract class User {
    private final int id;

    protected User(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid user id");
        }
        this.id = id;
    }

    public int getId() {
        return id;
    }
}