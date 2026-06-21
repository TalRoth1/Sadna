package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import java.util.List;
import java.util.UUID;

public interface IEventRepository {
    Event getById(UUID eventId);
    List<Event> getAll();
    void save(Event event);
    void delete(UUID eventId);

    /**
     * Flush only the ticket statuses (AVAILABLE/RESERVED/SOLD) of the given
     * event to durable storage. Required so that reservation/sale state set on
     * a domain {@link Event} survives — and is visible to the next read — when
     * the repository rebuilds a fresh {@code Event} per {@link #getById} call
     * (the JPA profile).
     *
     * <p>Default is a no-op: in-memory repositories store the live object that
     * was just mutated, so its statuses are already current. The JPA
     * repository overrides this to write the status column.
     */
    default void updateTicketStatuses(Event event) {
        // no-op for in-memory repositories
    }
}
