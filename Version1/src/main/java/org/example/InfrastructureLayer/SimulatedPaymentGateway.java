package org.example.InfrastructureLayer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentProvider;
import org.example.ApplicationLayer.PaymentResult;

/**
 * Development stub for the external payment-clearing system, as required
 * by the assignment ("Stubs should approve most payments to allow the
 * purchase flow to complete, but they must also provide a way to simulate
 * failures").
 *
 * Behaviour:
 *   - {@link #pay} and {@link #refund} default to APPROVE / SUCCEED.
 *   - One-shot toggles {@link #declineNextPayment} and
 *     {@link #failNextRefund} make the very next call return false, and
 *     auto-reset afterwards. That way you can deterministically test a
 *     single failure scenario without worrying about leaving the stub in
 *     a broken state.
 *
 * Both knobs are intended to be flipped through {@code DevStubController}
 * (or directly from a test). The stub itself never touches an external
 * provider — it only logs and returns.
 *
 * Thread-safety: outcomes are held in {@link AtomicReference} and consumed
 * via {@code getAndSet}, so concurrent {@code pay()} calls compete cleanly
 * for the single "next" outcome.
 */
public class SimulatedPaymentGateway implements IPaymentGateway, PaymentProvider {

    /** Provider id used to select this adapter from configuration. */
    public static final String PROVIDER_ID = "SIMULATED";

    public enum PayOutcome { APPROVE, DECLINE }
    public enum RefundOutcome { SUCCEED, FAIL }

    private static final Logger logger =
            Logger.getLogger(SimulatedPaymentGateway.class.getName());

    private final AtomicReference<PayOutcome> nextPayOutcome =
            new AtomicReference<>(PayOutcome.APPROVE);
    private final AtomicReference<RefundOutcome> nextRefundOutcome =
            new AtomicReference<>(RefundOutcome.SUCCEED);

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public PaymentResult pay(UUID userID, float amount, PaymentDetails paymentDetails) {
        PayOutcome outcome = nextPayOutcome.getAndSet(PayOutcome.APPROVE);

        logger.info("[SimulatedPaymentGateway] pay user=" + userID
                + " amount=" + amount + " -> " + outcome);

        if (outcome == PayOutcome.DECLINE) {
            return PaymentResult.failure();
        }

        return PaymentResult.success(10000);
    }

    @Override
    public boolean refund(int transactionId) {
        RefundOutcome outcome = nextRefundOutcome.getAndSet(RefundOutcome.SUCCEED);

        logger.info("[SimulatedPaymentGateway] refund transactionId=" + transactionId
                + " -> " + outcome);

        return outcome == RefundOutcome.SUCCEED;
    }

    // ---------- one-shot toggles ----------

    public void declineNextPayment() {
        nextPayOutcome.set(PayOutcome.DECLINE);
    }

    public void approveNextPayment() {
        nextPayOutcome.set(PayOutcome.APPROVE);
    }

    public void failNextRefund() {
        nextRefundOutcome.set(RefundOutcome.FAIL);
    }

    public void succeedNextRefund() {
        nextRefundOutcome.set(RefundOutcome.SUCCEED);
    }

    public PayOutcome peekNextPayOutcome() {
        return nextPayOutcome.get();
    }

    public RefundOutcome peekNextRefundOutcome() {
        return nextRefundOutcome.get();
    }
}
