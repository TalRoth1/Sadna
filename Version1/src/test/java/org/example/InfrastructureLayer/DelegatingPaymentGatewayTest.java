package org.example.InfrastructureLayer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentProvider;
import org.example.ApplicationLayer.PaymentResult;
import org.junit.jupiter.api.Test;

/**
 * Verifies the multi-provider behavior required by general requirement I.3:
 * the gateway keeps a registry of clearing services and routes each charge /
 * refund to the provider chosen by configuration. Mirrors
 * {@link DelegatingTicketingGatewayTest}.
 */
public class DelegatingPaymentGatewayTest {

    /** Minimal in-test provider that records what it was asked to do. */
    private static final class FakeProvider implements PaymentProvider {
        private final String id;
        private final int transactionId;
        private final List<Integer> refunded = new ArrayList<>();
        private int payCalls = 0;

        FakeProvider(String id, int transactionId) {
            this.id = id;
            this.transactionId = transactionId;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public boolean handshake() {
            return true;
        }

        @Override
        public PaymentResult pay(UUID userId, float amount, PaymentDetails paymentDetails) {
            payCalls++;
            return PaymentResult.success(transactionId);
        }

        @Override
        public boolean refund(int transactionId) {
            refunded.add(transactionId);
            return true;
        }
    }

    @Test
    public void pay_routesToConfiguredDefaultProvider() {
        FakeProvider sim = new FakeProvider("SIMULATED", 10000);
        FakeProvider ext = new FakeProvider("EXTERNAL", 20000);

        DelegatingPaymentGateway gateway =
                new DelegatingPaymentGateway(List.of(sim, ext), "EXTERNAL");

        PaymentResult result = gateway.pay(UUID.randomUUID(), 100f, new PaymentDetails());

        assertTrue(result.isSuccessful());
        assertEquals(20000, result.getTransactionId());
        assertEquals(1, ext.payCalls);
        assertEquals(0, sim.payCalls);
    }

    @Test
    public void changingDefault_switchesProviderWithoutCodeChange() {
        FakeProvider sim = new FakeProvider("SIMULATED", 10000);
        FakeProvider ext = new FakeProvider("EXTERNAL", 20000);

        DelegatingPaymentGateway gateway =
                new DelegatingPaymentGateway(List.of(sim, ext), "SIMULATED");

        gateway.pay(UUID.randomUUID(), 100f, new PaymentDetails());

        assertEquals(1, sim.payCalls);
        assertEquals(0, ext.payCalls);
    }

    @Test
    public void refund_delegatesToActiveProvider() {
        FakeProvider sim = new FakeProvider("SIMULATED", 10000);
        FakeProvider ext = new FakeProvider("EXTERNAL", 20000);

        DelegatingPaymentGateway gateway =
                new DelegatingPaymentGateway(List.of(sim, ext), "SIMULATED");

        boolean refunded = gateway.refund(555);

        assertTrue(refunded);
        assertEquals(List.of(555), sim.refunded);
        assertTrue(ext.refunded.isEmpty());
    }

    @Test
    public void unknownDefaultProvider_failsFastAtConstruction() {
        FakeProvider sim = new FakeProvider("SIMULATED", 10000);

        assertThrows(IllegalArgumentException.class,
                () -> new DelegatingPaymentGateway(List.of(sim), "DOES_NOT_EXIST"));
    }

    @Test
    public void duplicateProviderId_failsFastAtConstruction() {
        FakeProvider a = new FakeProvider("EXTERNAL", 10000);
        FakeProvider b = new FakeProvider("EXTERNAL", 20000);

        assertThrows(IllegalArgumentException.class,
                () -> new DelegatingPaymentGateway(List.of(a, b), "EXTERNAL"));
    }

    @Test
    public void pay_propagatesProviderResultOnDecline() {
        PaymentProvider declining = new PaymentProvider() {
            @Override
            public String providerId() {
                return "EXTERNAL";
            }

            @Override
            public boolean handshake() {
                return true;
            }

            @Override
            public PaymentResult pay(UUID userId, float amount, PaymentDetails paymentDetails) {
                return PaymentResult.failure();
            }

            @Override
            public boolean refund(int transactionId) {
                return false;
            }
        };

        DelegatingPaymentGateway gateway =
                new DelegatingPaymentGateway(List.of(declining), "EXTERNAL");

        assertDoesNotThrow(() -> {
            PaymentResult result = gateway.pay(UUID.randomUUID(), 50f, new PaymentDetails());
            assertTrue(!result.isSuccessful());
        });
    }
}
