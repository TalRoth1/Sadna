package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

public abstract class User {
    private final UUID id;
    float age;

    protected User(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public float getAge()
    {
        return this.age;
    }
}
