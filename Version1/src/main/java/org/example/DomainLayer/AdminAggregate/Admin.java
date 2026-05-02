package org.example.DomainLayer.AdminAggregate;

import java.util.UUID;

public interface Admin {
    String getUsername();
    UUID getId();
}