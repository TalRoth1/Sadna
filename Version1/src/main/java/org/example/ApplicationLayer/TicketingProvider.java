package org.example.ApplicationLayer;

import java.util.Set;
import java.util.UUID;

/**
 * Strategy port for a single external ticket-supply / issuance service
 * (general requirements I.4 + integrity rules: contact a market-recognised
 * supply system, send the package + customer details, receive an issuance
 * confirmation).
 *
 * <p>Each external system the platform integrates with is one concrete
 * implementation of this interface. {@link DelegatingTicketingGateway}
 * keeps a registry of them and routes each issuance to the configured
 * provider, which is how the "support more than one supply service"
 * requirement is satisfied without the domain layer knowing any provider
 * details.
 */
public interface TicketingProvider {

    /**
     * Stable identifier used to select this provider from configuration
     * (e.g. {@code "EXTERNAL"}, {@code "SIMULATED"}). Must be unique across
     * all registered providers.
     */
    String providerId();

    /**
     * Issue/register the given tickets with the external supply system.
     *
     * @return the supply confirmation — an external ticket identifier /
     *         secure code string (e.g. {@code TIX-1a2b-3456}).
     * @throws RuntimeException if the external system refused or could not
     *         be reached; the caller ({@code PurchaseDomainService}) treats
     *         this as a hard failure and runs the refund + release
     *         compensation.
     */
    String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds);

    /**
     * Cancel an already-issued ticket (used for system-initiated rollbacks).
     * Default is a no-op success so providers that cannot cancel still
     * satisfy the contract.
     */
    default boolean cancelTicket(String ticketId) {
        return true;
    }

    /**
     * Availability probe. Default returns {@code true} so simulated
     * providers need not override it; real providers ping the remote system.
     */
    default boolean handshake() {
        return true;
    }
}
