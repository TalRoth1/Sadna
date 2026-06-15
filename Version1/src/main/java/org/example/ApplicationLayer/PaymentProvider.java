package org.example.ApplicationLayer;

import java.util.UUID;

/**
 * Strategy port for a single external payment-clearing service (general
 * requirement I.3: contact a market-recognised clearing system, send the
 * transaction details, receive a success/failure confirmation).
 *
 * <p>Each clearing service the platform integrates with is one concrete
 * implementation of this interface. {@code DelegatingPaymentGateway} keeps a
 * registry of them and routes each charge / refund to the configured
 * provider, which is how the "support more than one clearing service"
 * requirement is satisfied without the domain layer knowing any provider
 * details. This mirrors {@link TicketingProvider} for ticket issuance (I.4).
 */
public interface PaymentProvider {

    /**
     * Stable identifier used to select this provider from configuration
     * (e.g. {@code "EXTERNAL"}, {@code "SIMULATED"}). Must be unique across
     * all registered providers.
     */
    String providerId();

    /**
     * Authorize + capture a charge. A declined card is a normal business
     * outcome returned as {@link PaymentResult#failure()} (not an exception);
     * a network/timeout problem is signalled by throwing.
     */
    PaymentResult pay(UUID userId, float amount, PaymentDetails paymentDetails);

    /**
     * Compensating reversal of a previously captured charge. Default returns
     * {@code true} so providers that cannot refund still satisfy the contract.
     */
    default boolean refund(int transactionId) {
        return true;
    }

    /**
     * Availability probe. Default returns {@code true} so simulated providers
     * need not override it; real providers ping the remote system.
     */
    default boolean handshake() {
        return true;
    }
}
