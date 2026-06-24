package org.example.AdditionalTests;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.InfrastructureLayer.ExternalTicketingGateway;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ExternalTicketingGatewayAdditionalTests {

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
        assertEquals(ExternalTicketingGateway.PROVIDER_ID, new ExternalTicketingGateway().providerId());
        assertEquals(ExternalTicketingGateway.PROVIDER_ID, new ExternalTicketingGateway(null).providerId());
        assertEquals(ExternalTicketingGateway.PROVIDER_ID, new ExternalTicketingGateway("   ").providerId());
    }

    @Test
    public void handshake_postsFormAndReturnsTrueOnlyForOkResponses() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> " ok ");

        assertTrue(gateway.handshake());
        assertEquals("action_type=handshake", requestBodies.get(0));

        stopServer();
        requestBodies.clear();

        ExternalTicketingGateway failed = gatewayWithServer(body -> "NOPE");

        assertFalse(failed.handshake());
    }

    @Test
    public void handshake_returnsFalseWhenExternalServiceReturnsHttpError() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> "not used", 503);

        assertFalse(gateway.handshake());
    }

    @Test
    public void issueTickets_validatesInputsBeforeCallingExternalService() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> "ticket-ref");
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> gateway.issueTickets(null, eventId, Set.of(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> gateway.issueTickets(userId, null, Set.of(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> gateway.issueTickets(userId, eventId, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.issueTickets(userId, eventId, Set.of()));

        assertTrue("validation should fail before any HTTP request", requestBodies.isEmpty());
    }

    @Test
    public void issueTickets_postsQuantityAndReturnsTrimmedReference() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> "  TIX-123  ");
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String reference = gateway.issueTickets(userId, eventId, Set.of(UUID.randomUUID(), UUID.randomUUID()));

        assertEquals("TIX-123", reference);
        String body = requestBodies.get(0);
        assertTrue(body.contains("action_type=issue_ticket"));
        assertTrue(body.contains("customer_id=" + userId));
        assertTrue(body.contains("event_id=" + eventId));
        assertTrue(body.contains("zone=general"));
        assertTrue(body.contains("quantity=2"));
    }

    @Test
    public void issueTickets_throwsMarkerExceptionForBlankOrMinusOneResponses() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> "-1");

        assertThrows(
                ExternalTicketingGateway.TicketingFailedException.class,
                () -> gateway.issueTickets(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()))
        );

        stopServer();
        requestBodies.clear();

        ExternalTicketingGateway blankGateway = gatewayWithServer(body -> "   ");

        assertThrows(
                ExternalTicketingGateway.TicketingFailedException.class,
                () -> blankGateway.issueTickets(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()))
        );
    }

    @Test
    public void cancelTicket_validatesInputAndMapsProviderResponseToBoolean() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> body.contains("ticket_id=A-123") ? "1" : "0");

        assertThrows(IllegalArgumentException.class, () -> gateway.cancelTicket(null));
        assertThrows(IllegalArgumentException.class, () -> gateway.cancelTicket("   "));

        assertTrue(gateway.cancelTicket("A-123"));

        ExternalTicketingGateway.TicketingFailedException error = assertThrows(
                ExternalTicketingGateway.TicketingFailedException.class,
                () -> gateway.cancelTicket("B 123")
        );

        assertTrue(error.getMessage().contains("unexpected cancellation response: 0"));

        assertTrue(requestBodies.get(0).contains("action_type=cancel_ticket"));
        assertTrue(requestBodies.get(0).contains("ticket_id=A-123"));
        assertTrue(requestBodies.get(1).contains("ticket_id=B+123"));
    }

    @Test
    public void cancelTicket_propagatesExternalHttpErrors() throws Exception {
        ExternalTicketingGateway gateway = gatewayWithServer(body -> "not used", 500);

        ExternalTicketingGateway.TicketingFailedException error = assertThrows(
                ExternalTicketingGateway.TicketingFailedException.class,
                () -> gateway.cancelTicket("A-123")
        );

        assertTrue(error.getMessage().contains("External ticketing service returned HTTP"));
    }

    private ExternalTicketingGateway gatewayWithServer(Function<String, String> responder) throws IOException {
        return gatewayWithServer(responder, 200);
    }

    private ExternalTicketingGateway gatewayWithServer(Function<String, String> responder, int statusCode) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, responder, statusCode));
        server.start();
        return new ExternalTicketingGateway("http://127.0.0.1:" + server.getAddress().getPort() + "/");
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
}
