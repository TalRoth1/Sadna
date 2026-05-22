package org.example.InfrastructureLayer;

import java.util.UUID;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.PaymentDetails;

/**
 * Development-only payment gateway. Always reports a successful charge so
 * the purchase flow can be exercised end-to-end without a real PSP. Replace
 * with a real provider integration before going to production.
 */
public class NoopPaymentGateway implements IPaymentGateway {

    @Override
    public boolean pay(UUID userID, float amount, PaymentDetails paymentDetails) {
        return true;
    }
}
