package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.TicketingProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExternalTicketingGateway implements TicketingProvider {
    private static final Logger logger = Logger.getLogger(ExternalTicketingGateway.class.getName());

    /** Provider id used to select this adapter from configuration. */
    public static final String PROVIDER_ID = "EXTERNAL";

    private static final String DEFAULT_SERVICE_URL =
            "https://damp-lynna-wsep-1984852e.koyeb.app/";

    private final URI ticketingServiceUri;
    private final HttpClient httpClient;

    public ExternalTicketingGateway() {
        this(DEFAULT_SERVICE_URL);
    }

    public ExternalTicketingGateway(String serviceUrl) {
        this.ticketingServiceUri = URI.create(
                serviceUrl == null || serviceUrl.isBlank() ? DEFAULT_SERVICE_URL : serviceUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean handshake() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "handshake");

        try {
            String response = postForm(params);
            boolean success = "OK".equalsIgnoreCase(response.trim());

            logger.info("[ExternalTicketingGateway] handshake result=" + response);
            return success;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[ExternalTicketingGateway] handshake failed", e);
            return false;
        }
    }

    @Override
    public String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required for ticket issuing");
        }

        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required for ticket issuing");
        }

        if (ticketIds == null || ticketIds.isEmpty()) {
            throw new IllegalArgumentException("Ticket IDs are required for ticket issuing");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "issue_ticket");
        params.put("customer_id", userId.toString());
        params.put("event_id", eventId.toString());
        params.put("zone", "general");
        params.put("quantity", Integer.toString(ticketIds.size()));

        try {
            String response = postForm(params);

            logger.info("[ExternalTicketingGateway] issueTickets user=" + userId
                    + ", event=" + eventId
                    + ", quantity=" + ticketIds.size()
                    + ", response=" + response);

            if (response == null || response.isBlank()) {
                throw new TicketingFailedException(
                        "External ticketing service returned an empty response. Tickets were not issued."
                );
            }

            String ticketReference = response.trim();

            if ("-1".equals(ticketReference)) {
                throw new TicketingFailedException(
                        "External ticketing service failed to issue tickets for event "
                                + eventId + " and user " + userId + "."
                );
            }

            if (!ticketReference.startsWith("TIX-")) {
                throw new TicketingFailedException(
                        "External ticketing service returned an unexpected ticket reference: "
                                + ticketReference
                );
            }

            return ticketReference;
        } catch (TicketingFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE,
                    "[ExternalTicketingGateway] issueTickets failed for user=" + userId
                            + ", event=" + eventId,
                    e);
            throw e;
        }
    }

    @Override
    public boolean cancelTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException("Ticket ID is required for ticket cancellation");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "cancel_ticket");
        params.put("ticket_id", ticketId);

        try {
            String response = postForm(params);
            String result = response == null ? "" : response.trim();

            logger.info("[ExternalTicketingGateway] cancelTicket ticketId=" + ticketId
                    + ", result=" + result);

            if ("1".equals(result)) {
                return true;
            }

            if ("-1".equals(result)) {
                throw new TicketingFailedException(
                        "External ticketing service failed to cancel ticket " + ticketId + "."
                );
            }

            throw new TicketingFailedException(
                    "External ticketing service returned an unexpected cancellation response: " + result
            );
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE,
                    "[ExternalTicketingGateway] cancelTicket failed for ticketId=" + ticketId,
                    e);
            throw e;
        }
    }

    private String postForm(Map<String, String> params) {
        String body = encodeForm(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(ticketingServiceUri)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TicketingFailedException(
                        "External ticketing service returned HTTP " + response.statusCode()
                                + ". Response body: " + safeBody(response.body())
                );
            }

            return response.body() == null ? "" : response.body().trim();
        } catch (IOException e) {
            throw new TicketingFailedException(
                    "External ticketing service is not reachable or timed out. Tickets could not be issued or cancelled.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TicketingFailedException(
                    "External ticketing request was interrupted before completion.",
                    e
            );
        }
    }

    private String safeBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }

        String trimmed = body.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }

    private String encodeForm(Map<String, String> params) {
        StringBuilder encoded = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append("&");
            }

            encoded.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
        }

        return encoded.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static class TicketingFailedException extends RuntimeException {
        public TicketingFailedException(String message) {
            super(message);
        }

        public TicketingFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}