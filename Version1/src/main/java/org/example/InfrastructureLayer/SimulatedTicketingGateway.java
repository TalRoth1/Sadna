package org.example.InfrastructureLayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.example.ApplicationLayer.ITicketingGateway;

/**
 * Development stub for the external ticketing system (the service that
 * would otherwise generate PDFs / barcodes and deliver them to the buyer).
 *
 * Mirrors {@link SimulatedPaymentGateway}: defaults to SUCCEED, with a
 * one-shot {@link #failNextIssue} toggle that makes the very next call
 * throw and then auto-resets. {@code PurchaseDomainService.completePurchase}
 * is responsible for catching the failure and running a compensating
 * refund against the payment gateway — that's the cancellation+refund
 * scenario the assignment asks us to exercise.
 */
public class SimulatedTicketingGateway implements ITicketingGateway {

    public enum Outcome { SUCCEED, FAIL }

    /**
     * Marker exception so the domain layer can distinguish a deliberate
     * ticketing-system failure from any other RuntimeException coming out
     * of the call stack. Extends RuntimeException so the gateway interface
     * doesn't have to advertise a checked exception.
     */
    public static class TicketingFailedException extends RuntimeException {
        public TicketingFailedException(String message) {
            super(message);
        }
    }

    private static final Logger logger =
            Logger.getLogger(SimulatedTicketingGateway.class.getName());

    private final AtomicReference<Outcome> nextOutcome =
            new AtomicReference<>(Outcome.SUCCEED);

    @Override
    public void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
        Outcome outcome = nextOutcome.getAndSet(Outcome.SUCCEED);
        logger.info("[SimulatedTicketingGateway] issueTickets user=" + userId
                + " event=" + eventId + " tickets=" + ticketIds + " -> " + outcome);
        if (outcome == Outcome.FAIL) {
            throw new TicketingFailedException(
                    "Simulated ticketing failure for event " + eventId);
        }
    }

    // ---------- one-shot toggle ----------

    public void failNextIssue() {
        nextOutcome.set(Outcome.FAIL);
    }

    public void succeedNextIssue() {
        nextOutcome.set(Outcome.SUCCEED);
    }

    public Outcome peekNextOutcome() {
        return nextOutcome.get();
    }
}
