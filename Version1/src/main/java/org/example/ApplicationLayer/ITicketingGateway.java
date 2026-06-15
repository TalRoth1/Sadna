package org.example.ApplicationLayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ITicketingGateway
{
    String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds);

    /**
     * System-initiated rollback: cancel ticket codes that were already
     * issued by the external supply system when a later step of the
     * purchase fails. Best-effort — implementations must not throw, so a
     * cancellation problem never masks the original failure.
     *
     * <p>Declared {@code default} (no-op) so this interface stays a
     * functional interface: the simple lambda gateways used in tests keep
     * compiling, while {@code DelegatingTicketingGateway} provides the real
     * implementation.
     */
    default void cancelTickets(List<String> ticketReferences) {
        // no-op by default
    }
}
