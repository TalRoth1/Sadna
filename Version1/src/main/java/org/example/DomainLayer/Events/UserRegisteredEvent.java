package org.example.DomainLayer.Events;

import java.util.UUID;

/**
 * Domain event raised by {@code UserService} immediately after a new user
 * account is successfully persisted.
 *
 * <h2>DDD / Clean-Architecture constraints</h2>
 * <ul>
 *   <li>Immutable value object — all fields are {@code final}.</li>
 *   <li>No framework annotations — this class lives in the Domain layer
 *       and must remain completely framework-agnostic.</li>
 *   <li>No behaviour — the event is a pure data carrier; all reactions
 *       to it (e.g. recording the subscription metric) belong in the
 *       Application-layer {@code SystemMetricsCollector}.</li>
 * </ul>
 */
public class UserRegisteredEvent implements IDomainEvent {

    private final UUID userId;

    public UserRegisteredEvent(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserRegisteredEvent.userId must not be null");
        }
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
