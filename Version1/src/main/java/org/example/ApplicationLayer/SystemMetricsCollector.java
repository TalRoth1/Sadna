package org.example.ApplicationLayer;

import org.example.DomainLayer.Events.IDomainEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.Events.TicketReservedEvent;
import org.example.DomainLayer.Events.UserRegisteredEvent;

/**
 * Application-layer event handler that bridges domain events to the
 * {@link ISystemMetricsTracker} port.
 *
 * <h2>Single Responsibility</h2>
 * <p>This class has exactly one job: inspect the runtime type of an incoming
 * {@link IDomainEvent} and call the appropriate {@code record*()} method on
 * the tracker.  It contains no business logic, no computation, and no I/O.
 *
 * <h2>Open/Closed</h2>
 * <p>To support a new metric, add a new event class and extend the
 * {@code handle} dispatch block — no existing logic needs to change.
 *
 * <h2>Framework-agnostic</h2>
 * <p>No Spring annotations.  This bean is wired manually in {@code BeanConfig}
 * and subscribed to the {@link EventPublisher} there.
 *
 * <h2>Null / unknown event handling</h2>
 * <ul>
 *   <li>A {@code null} event throws {@link IllegalArgumentException}.</li>
 *   <li>An unrecognised event type is silently ignored — the collector is
 *       closed to modification but open to extension, so future event types
 *       must not crash existing subscribers.</li>
 * </ul>
 */
public class SystemMetricsCollector {

    private final ISystemMetricsTracker metricsTracker;

    public SystemMetricsCollector(ISystemMetricsTracker metricsTracker) {
        if (metricsTracker == null) {
            throw new IllegalArgumentException("ISystemMetricsTracker must not be null");
        }
        this.metricsTracker = metricsTracker;
    }

    /**
     * Dispatches the event to the appropriate tracker method.
     *
     * <p>This method is registered as a lambda reference
     * ({@code collector::handle}) with the {@link EventPublisher} in
     * {@code BeanConfig}, so it receives every domain event published by
     * any service in the application.
     *
     * @param event the domain event to process; must not be {@code null}
     * @throws IllegalArgumentException if {@code event} is {@code null}
     */
    public void handle(IDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("IDomainEvent must not be null");
        }

        if (event instanceof UserRegisteredEvent) {
            metricsTracker.recordSubscription();
        } else if (event instanceof TicketReservedEvent) {
            metricsTracker.recordReservation();
        } else if (event instanceof PurchaseCompletedEvent) {
            metricsTracker.recordPurchase();
        }
        // Unknown event types are silently ignored (Open/Closed Principle).
    }
}
