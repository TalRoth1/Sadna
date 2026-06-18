package org.example.AdditionalTests;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentResult;
import org.example.InfrastructureLayer.ExternalPaymentGateway;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ExternalPaymentGatewayAdditionalTests {

    private HttpServer server;
    private final List<String> requestBodies = new ArrayList<>();

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    public void providerIdAndFallbackConstructors_doNotRequireNetworkCalls() {
        assertEquals(ExternalPaymentGateway.PROVIDER_ID, new ExternalPaymentGateway().providerId());
        assertEquals(ExternalPaymentGateway.PROVIDER_ID, new ExternalPaymentGateway(null).providerId());
        assertEquals(ExternalPaymentGateway.PROVIDER_ID, new ExternalPaymentGateway("   ").providerId());
    }

    @Test
    public void handshake_postsFormAndReturnsTrueOnlyForOkResponses() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "OK");

        assertTrue(gateway.handshake());
        assertEquals("action_type=handshake", requestBodies.get(0));

        stopServer();
        requestBodies.clear();

        ExternalPaymentGateway failed = gatewayWithServer(body -> "NOPE");

        assertFalse(failed.handshake());
        assertEquals("action_type=handshake", requestBodies.get(0));
    }

    @Test
    public void handshake_returnsFalseWhenExternalServiceReturnsHttpError() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "not used", 500);

        assertFalse(gateway.handshake());
    }

    @Test
    public void pay_sendsEncodedPayloadAndAcceptsValidTransactionId() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "10000");
        PaymentDetails details = paymentDetails("", "4580458045804580", "01", "2030", "Ido Levi", "123", "123456789");

        PaymentResult result = gateway.pay(UUID.randomUUID(), 19.5f, details);

        assertTrue(paymentSucceeded(result));
        assertEquals(Integer.valueOf(10000), transactionId(result));

        String body = requestBodies.get(0);
        assertTrue(body.contains("action_type=pay"));
        assertTrue(body.contains("amount=19.5"));
        assertTrue(body.contains("currency=ILS"));
        assertTrue(body.contains("holder=Ido+Levi"));
        assertTrue(body.contains("card_number=4580458045804580"));
        assertTrue(body.contains("id=123456789"));
    }

    @Test
    public void pay_trimsCurrencyAndReturnsFailureForOutOfRangeTransactionId() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "9999");
        PaymentDetails details = paymentDetails(" USD ", "4580458045804580", "01", "2030", "Alice", "123", "123456789");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> gateway.pay(UUID.randomUUID(), 1f, details)
        );

        assertTrue(error.getMessage().contains("unexpected transaction id: 9999"));
        assertTrue(requestBodies.get(0).contains("currency=USD"));
    }

    @Test
    public void pay_throwsWhenExternalResponseIsNotAnInteger() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "not-a-number");

        assertThrows(
                IllegalStateException.class,
                () -> gateway.pay(UUID.randomUUID(), 1f,
                        paymentDetails("ILS", "4580458045804580", "01", "2030", "Alice", "123", "123456789"))
        );
    }

    @Test
    public void pay_validatesRequiredPaymentDetailsBeforeCallingExternalService() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> "10000");

        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", " ", "01", "2030", "Alice", "123", "123456789")));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", "4580458045804580", " ", "2030", "Alice", "123", "123456789")));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", "4580458045804580", "01", " ", "Alice", "123", "123456789")));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", "4580458045804580", "01", "2030", " ", "123", "123456789")));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", "4580458045804580", "01", "2030", "Alice", " ", "123456789")));
        assertThrows(IllegalArgumentException.class, () -> gateway.pay(UUID.randomUUID(), 1f,
                paymentDetails("ILS", "4580458045804580", "01", "2030", "Alice", "123", " ")));

        assertTrue("validation should fail before any HTTP request", requestBodies.isEmpty());
    }

    @Test
    public void refund_validatesRangeAndMapsExternalResponseToBoolean() throws Exception {
        ExternalPaymentGateway gateway = gatewayWithServer(body -> {
            if (body.contains("transaction_id=10000")) {
                return "1";
            }
            return "0";
        });

        assertThrows(IllegalArgumentException.class, () -> gateway.refund(9999));
        assertThrows(IllegalArgumentException.class, () -> gateway.refund(100001));

        assertTrue(gateway.refund(10000));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> gateway.refund(10001)
        );

        assertTrue(error.getMessage().contains("unexpected refund response: 0"));

        assertTrue(requestBodies.get(0).contains("action_type=refund"));
        assertTrue(requestBodies.get(0).contains("transaction_id=10000"));
        assertTrue(requestBodies.get(1).contains("transaction_id=10001"));
    }

    private ExternalPaymentGateway gatewayWithServer(Function<String, String> responder) throws IOException {
        return gatewayWithServer(responder, 200);
    }

    private ExternalPaymentGateway gatewayWithServer(Function<String, String> responder, int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, responder, statusCode));
        server.start();
        return new ExternalPaymentGateway("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private void respond(HttpExchange exchange, Function<String, String> responder, int statusCode) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requestBodies.add(body);

        byte[] bytes = responder.apply(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private PaymentDetails paymentDetails(String currency,
                                          String cardNumber,
                                          String month,
                                          String year,
                                          String holder,
                                          String cvv,
                                          String id) {
        try {
            PaymentDetails details = mock(PaymentDetails.class);
            set(details, "currency", currency);
            set(details, "cardNumber", cardNumber);
            set(details, "month", month);
            set(details, "year", year);
            set(details, "holder", holder);
            set(details, "cvv", cvv);
            set(details, "id", id);
            return details;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not create PaymentDetails for test", e);
        }
    }

    private void set(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
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
