package org.example.AdditionalTests;

import org.example.ApplicationLayer.PaymentResult;
import org.example.InfrastructureLayer.SimulatedPaymentGateway;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.Assert.*;

public class SimulatedPaymentGatewayAdditionalTests {

    @Test
    public void providerIdAndDefaultOutcomes_areSuccessfulAndStable() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

        assertEquals(SimulatedPaymentGateway.PROVIDER_ID, gateway.providerId());
        assertEquals(SimulatedPaymentGateway.PayOutcome.APPROVE, gateway.peekNextPayOutcome());
        assertEquals(SimulatedPaymentGateway.RefundOutcome.SUCCEED, gateway.peekNextRefundOutcome());

        PaymentResult result = gateway.pay(UUID.randomUUID(), 42.5f, null);

        assertTrue(paymentSucceeded(result));
        assertEquals(Integer.valueOf(10000), transactionId(result));
        assertTrue(gateway.refund(10000));
    }

    @Test
    public void oneShotPaymentOutcome_declinesOnlyTheNextPaymentAndThenResets() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

        gateway.declineNextPayment();
        assertEquals(SimulatedPaymentGateway.PayOutcome.DECLINE, gateway.peekNextPayOutcome());

        PaymentResult declined = gateway.pay(UUID.randomUUID(), 10f, null);

        assertFalse(paymentSucceeded(declined));
        assertEquals(SimulatedPaymentGateway.PayOutcome.APPROVE, gateway.peekNextPayOutcome());

        PaymentResult approvedAfterReset = gateway.pay(UUID.randomUUID(), 10f, null);

        assertTrue(paymentSucceeded(approvedAfterReset));

        gateway.declineNextPayment();
        gateway.approveNextPayment();

        assertEquals(SimulatedPaymentGateway.PayOutcome.APPROVE, gateway.peekNextPayOutcome());
        assertTrue(paymentSucceeded(gateway.pay(UUID.randomUUID(), 10f, null)));
    }

    @Test
    public void oneShotRefundOutcome_failsOnlyTheNextRefundAndThenResets() {
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();

        gateway.failNextRefund();
        assertEquals(SimulatedPaymentGateway.RefundOutcome.FAIL, gateway.peekNextRefundOutcome());

        assertFalse(gateway.refund(12345));
        assertEquals(SimulatedPaymentGateway.RefundOutcome.SUCCEED, gateway.peekNextRefundOutcome());
        assertTrue(gateway.refund(12345));

        gateway.failNextRefund();
        gateway.succeedNextRefund();

        assertEquals(SimulatedPaymentGateway.RefundOutcome.SUCCEED, gateway.peekNextRefundOutcome());
        assertTrue(gateway.refund(12345));
    }

    private boolean paymentSucceeded(PaymentResult result) {
        Object value = readBooleanLike(result, "success", "successful", "approved", "succeeded");
        assertNotNull("PaymentResult should expose a success boolean", value);
        return (Boolean) value;
    }

    private Integer transactionId(PaymentResult result) {
        Object value = readValue(result, "transactionId", "transactionID", "paymentId", "paymentID");
        return value instanceof Number number ? number.intValue() : null;
    }

    private Object readBooleanLike(Object target, String... names) {
        Object value = readValue(target, names);
        return value instanceof Boolean ? value : null;
    }

    private Object readValue(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
            String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            for (String getterName : new String[] {"is" + capitalized, "get" + capitalized}) {
                try {
                    Method method = target.getClass().getMethod(getterName);
                    return method.invoke(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
