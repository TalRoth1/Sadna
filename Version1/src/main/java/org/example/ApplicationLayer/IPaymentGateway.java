package org.example.ApplicationLayer;

import java.util.UUID;

/**
 * Adapter for the external payment-clearing system.
 *
 * Two operations:
 *   - {@link #pay}: authorize + capture a charge. Returns {@code true} when
 *     the PSP approved, {@code false} when it declined. By convention the
 *     gateway does NOT throw on a normal "declined" — that's a regular
 *     business outcome the domain knows how to handle by leaving the
 *     reservation in place so the user can retry with another card.
 *   - {@link #refund}: compensating transaction the domain calls when it
 *     committed a charge but a downstream step (ticketing) failed. Default
 *     implementation returns {@code true}, which keeps the existing test
 *     lambdas {@code (uid, amount, details) -> true} compiling without
 *     change; only stubs that need to simulate a refund failure need to
 *     override it.
 */
public interface IPaymentGateway
{
    boolean pay(UUID userID, float amount, PaymentDetails paymentDetails);

    default boolean refund(UUID userID, float amount, PaymentDetails paymentDetails) {
        return true;
    }
}
