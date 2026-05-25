package org.example.DomainLayer.Events;

import java.util.UUID;

/**
 * Domain event raised by {@code PurchaseService} immediately after a user
 * successfully reserves tickets for an event (sitting or standing).
 *
 * <h2>DDD / Clean-Architecture constraints</h2>
 * <ul>
 *   <li>Immutable value object — all fields are {@code final}.</li>
 *   <li>No framework annotations — this class lives in the Domain layer
 *       and must remain completely framework-agnostic.</li>
 *   <li>No behaviour — the event is a pure data carrier; all reactions
 *       (e.g. recording the reservation metric) belong in the
 *       Application-layer {@code SystemMetricsCollector}.</li>
 * </ul>
 *
 * <p>Note: a "reservation" in this context is the act of an authenticated
 * user locking one or more tickets via {@code selectSittingTickets} or
 * {@code selectStandingTickets}, before payment is confirmed.
 */
public class TicketReservedEvent implements IDomainEvent {

    private final UUID userId;
    private final UUID eventId;

    public TicketReservedEvent(UUID userId, UUID eventId) {
        if (userId == null) {
            throw new IllegalArgumentException("TicketReservedEvent.userId must not be null");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("TicketReservedEvent.eventId must not be null");
        }
        this.userId  = userId;
        this.eventId = eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }
}
