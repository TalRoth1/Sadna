package org.example.ApplicationLayer;

import org.example.ApplicationLayer.ISystemMetricsTracker;
import org.example.DomainLayer.Events.IDomainEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.Events.TicketReservedEvent;
import org.example.DomainLayer.Events.UserRegisteredEvent;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * TDD — RED phase.
 *
 * <h2>What is under test</h2>
 * <p>{@link SystemMetricsCollector} is an Application-layer service that
 * subscribes to domain events via {@link EventPublisher} and delegates to
 * {@link ISystemMetricsTracker} to record the appropriate metric whenever
 * a relevant event fires.  It is the single wiring point between the
 * event-publishing services (UserService, PurchaseService) and the
 * rate-tracking infrastructure.
 *
 * <h2>Why these tests fail initially</h2>
 * <ul>
 *   <li>{@code ISystemMetricsTracker} does not exist yet.</li>
 *   <li>{@code SystemMetricsCollector} does not exist yet.</li>
 *   <li>{@code UserRegisteredEvent} does not exist yet.</li>
 *   <li>{@code TicketReservedEvent} does not exist yet.</li>
 * </ul>
 * All four must be created in Stage 3 before any test here turns green.
 *
 * <h2>Design invariants verified</h2>
 * <ul>
 *   <li><b>SRP</b> — the collector does nothing but route events to the
 *       tracker; no business logic lives here.</li>
 *   <li><b>DIP</b> — the collector depends on the {@code ISystemMetricsTracker}
 *       interface, never on the concrete {@code InMemorySystemMetricsTracker}.</li>
 *   <li><b>Closed under unknown events</b> — unrecognised event types must
 *       be silently ignored; the collector must never throw.</li>
 * </ul>
 */
public class SystemMetricsCollectorTest {

    private ISystemMetricsTracker trackerMock;
    private SystemMetricsCollector collector;

    @Before
    public void setUp() {
        trackerMock = mock(ISystemMetricsTracker.class);
        collector   = new SystemMetricsCollector(trackerMock);
    }

    // ====================================================================
    // 1. Constructor guard clauses
    // ====================================================================

    @Test
    public void constructor_nullTracker_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SystemMetricsCollector(null)
        );
    }

    // ====================================================================
    // 2. UserRegisteredEvent — records a subscription, nothing else
    // ====================================================================

    @Test
    public void handle_userRegisteredEvent_callsRecordSubscriptionOnce() {
        IDomainEvent event = new UserRegisteredEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, times(1)).recordSubscription();
    }

    @Test
    public void handle_userRegisteredEvent_doesNotCallRecordReservation() {
        IDomainEvent event = new UserRegisteredEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordReservation();
    }

    @Test
    public void handle_userRegisteredEvent_doesNotCallRecordPurchase() {
        IDomainEvent event = new UserRegisteredEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordPurchase();
    }

    @Test
    public void handle_multipleUserRegisteredEvents_callsRecordSubscriptionForEach() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            collector.handle(new UserRegisteredEvent(UUID.randomUUID()));
        }

        verify(trackerMock, times(count)).recordSubscription();
        verify(trackerMock, never()).recordReservation();
        verify(trackerMock, never()).recordPurchase();
    }

    // ====================================================================
    // 3. TicketReservedEvent — records a reservation, nothing else
    // ====================================================================

    @Test
    public void handle_ticketReservedEvent_callsRecordReservationOnce() {
        IDomainEvent event = new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, times(1)).recordReservation();
    }

    @Test
    public void handle_ticketReservedEvent_doesNotCallRecordSubscription() {
        IDomainEvent event = new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordSubscription();
    }

    @Test
    public void handle_ticketReservedEvent_doesNotCallRecordPurchase() {
        IDomainEvent event = new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordPurchase();
    }

    @Test
    public void handle_multipleTicketReservedEvents_callsRecordReservationForEach() {
        int count = 3;
        for (int i = 0; i < count; i++) {
            collector.handle(new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID()));
        }

        verify(trackerMock, times(count)).recordReservation();
        verify(trackerMock, never()).recordSubscription();
        verify(trackerMock, never()).recordPurchase();
    }

    // ====================================================================
    // 4. PurchaseCompletedEvent — records a purchase, nothing else
    // ====================================================================

    @Test
    public void handle_purchaseCompletedEvent_callsRecordPurchaseOnce() {
        IDomainEvent event = new PurchaseCompletedEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, times(1)).recordPurchase();
    }

    @Test
    public void handle_purchaseCompletedEvent_doesNotCallRecordSubscription() {
        IDomainEvent event = new PurchaseCompletedEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordSubscription();
    }

    @Test
    public void handle_purchaseCompletedEvent_doesNotCallRecordReservation() {
        IDomainEvent event = new PurchaseCompletedEvent(UUID.randomUUID());

        collector.handle(event);

        verify(trackerMock, never()).recordReservation();
    }

    @Test
    public void handle_multiplePurchaseCompletedEvents_callsRecordPurchaseForEach() {
        int count = 4;
        for (int i = 0; i < count; i++) {
            collector.handle(new PurchaseCompletedEvent(UUID.randomUUID()));
        }

        verify(trackerMock, times(count)).recordPurchase();
        verify(trackerMock, never()).recordSubscription();
        verify(trackerMock, never()).recordReservation();
    }

    // ====================================================================
    // 5. Mixed event stream — each event type routes to the correct method
    // ====================================================================

    @Test
    public void handle_mixedEventStream_routesEachEventToCorrectTrackerMethod() {
        collector.handle(new UserRegisteredEvent(UUID.randomUUID()));
        collector.handle(new UserRegisteredEvent(UUID.randomUUID()));  // 2 subscriptions
        collector.handle(new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID())); // 1 reservation
        collector.handle(new PurchaseCompletedEvent(UUID.randomUUID()));
        collector.handle(new PurchaseCompletedEvent(UUID.randomUUID()));
        collector.handle(new PurchaseCompletedEvent(UUID.randomUUID())); // 3 purchases

        verify(trackerMock, times(2)).recordSubscription();
        verify(trackerMock, times(1)).recordReservation();
        verify(trackerMock, times(3)).recordPurchase();
    }

    // ====================================================================
    // 6. Unknown / null events — must not throw, must not call tracker
    // ====================================================================

    @Test
    public void handle_nullEvent_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> collector.handle(null)
        );
    }

    @Test
    public void handle_unknownEventType_doesNotCallAnyTrackerMethod() {
        // An anonymous IDomainEvent implementation that is not one of the
        // three recognised types.  The collector must silently ignore it.
        IDomainEvent unknownEvent = new IDomainEvent() {};

        collector.handle(unknownEvent);

        verifyNoInteractions(trackerMock);
    }

    @Test
    public void handle_unknownEventType_doesNotThrow() {
        IDomainEvent unknownEvent = new IDomainEvent() {};

        // must complete without throwing
        collector.handle(unknownEvent);
    }

    // ====================================================================
    // 7. EventPublisher integration — the collector wires into the
    //    existing publisher infrastructure without modification.
    // ====================================================================

    /**
     * Verifies that when a {@link UserRegisteredEvent} is published through
     * the real {@link EventPublisher}, the collector's {@code handle} method
     * is invoked and delegates to the tracker.
     *
     * <p>This is an integration smoke-test for the wiring that {@code BeanConfig}
     * will establish in production.  It uses the real (non-mocked) publisher.
     */
    @Test
    public void eventPublisher_firesUserRegisteredEvent_collectorReceivesAndRecordsIt() {
        EventPublisher publisher = new EventPublisher();
        publisher.subscribe(collector::handle);

        publisher.publish(new UserRegisteredEvent(UUID.randomUUID()));

        verify(trackerMock, times(1)).recordSubscription();
    }

    @Test
    public void eventPublisher_firesTicketReservedEvent_collectorReceivesAndRecordsIt() {
        EventPublisher publisher = new EventPublisher();
        publisher.subscribe(collector::handle);

        publisher.publish(new TicketReservedEvent(UUID.randomUUID(), UUID.randomUUID()));

        verify(trackerMock, times(1)).recordReservation();
    }

    @Test
    public void eventPublisher_firesPurchaseCompletedEvent_collectorReceivesAndRecordsIt() {
        EventPublisher publisher = new EventPublisher();
        publisher.subscribe(collector::handle);

        publisher.publish(new PurchaseCompletedEvent(UUID.randomUUID()));

        verify(trackerMock, times(1)).recordPurchase();
    }

    /**
     * Verifies that the collector and the existing {@link EventListener}
     * (which handles notifications) can both be subscribed to the same
     * publisher simultaneously.  Publishing one event must invoke both
     * handlers without interference.
     */
    @Test
    public void eventPublisher_multipleSubscribers_allReceiveEvent() {
        // Arrange — second subscriber counts how many times it was called
        int[] secondSubscriberCallCount = {0};
        EventPublisher publisher = new EventPublisher();
        publisher.subscribe(collector::handle);
        publisher.subscribe(event -> secondSubscriberCallCount[0]++);

        // Act
        publisher.publish(new PurchaseCompletedEvent(UUID.randomUUID()));

        // Assert — collector recorded the purchase AND the second subscriber fired
        verify(trackerMock, times(1)).recordPurchase();
        assertEquals(1, secondSubscriberCallCount[0]);
    }
}
