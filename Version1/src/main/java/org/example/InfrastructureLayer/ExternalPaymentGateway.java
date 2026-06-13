package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentProvider;
import org.example.ApplicationLayer.PaymentResult;

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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExternalPaymentGateway implements IPaymentGateway, PaymentProvider {
    private static final Logger logger = Logger.getLogger(ExternalPaymentGateway.class.getName());

    private static final String DEFAULT_SERVICE_URL =
            "https://damp-lynna-wsep-1984852e.koyeb.app/";

    /** Provider id used to select this adapter from configuration. */
    public static final String PROVIDER_ID = "EXTERNAL";

    private final URI paymentServiceUri;
    private final HttpClient httpClient;

    public ExternalPaymentGateway() {
        this(DEFAULT_SERVICE_URL);
    }

    public ExternalPaymentGateway(String serviceUrl) {
        this.paymentServiceUri = URI.create(
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

            logger.info("[ExternalPaymentGateway] handshake result=" + response);
            return success;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[ExternalPaymentGateway] handshake failed", e);
            return false;
        }
    }

    @Override
    public PaymentResult pay(UUID userID, float amount, PaymentDetails paymentDetails) {
        validatePaymentDetails(paymentDetails);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "pay");
        params.put("amount", Float.toString(amount));
        params.put("currency", normalizeCurrency(paymentDetails.currency));
        params.put("card_number", paymentDetails.cardNumber);
        params.put("month", paymentDetails.month);
        params.put("year", paymentDetails.year);
        params.put("holder", paymentDetails.holder);
        params.put("cvv", paymentDetails.cvv);
        params.put("id", paymentDetails.id);

        try {
            String response = postForm(params);
            int transactionId = parseIntegerResponse(response);

            logger.info("[ExternalPaymentGateway] pay user=" + userID
                    + ", amount=" + amount
                    + ", transactionId=" + transactionId);

            if (transactionId >= 10000 && transactionId <= 100000) {
                return PaymentResult.success(transactionId);
            }

            return PaymentResult.failure();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE,
                    "[ExternalPaymentGateway] pay failed for user=" + userID
                            + ", amount=" + amount,
                    e);
            throw e;
        }
    }

    @Override
    public boolean refund(int transactionId) {
        if (transactionId < 10000 || transactionId > 100000) {
            throw new IllegalArgumentException("Valid transaction ID is required for refund");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action_type", "refund");
        params.put("transaction_id", Integer.toString(transactionId));

        try {
            String response = postForm(params);
            int result = parseIntegerResponse(response);

            logger.info("[ExternalPaymentGateway] refund transactionId=" + transactionId
                    + ", result=" + result);

            return result == 1;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE,
                    "[ExternalPaymentGateway] refund failed for transactionId=" + transactionId,
                    e);
            throw e;
        }
    }

    private String postForm(Map<String, String> params) {
        String body = encodeForm(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(paymentServiceUri)
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
                throw new IllegalStateException(
                        "External payment service returned HTTP " + response.statusCode()
                );
            }

            return response.body() == null ? "" : response.body().trim();
        } catch (IOException e) {
            throw new IllegalStateException("External payment service is not reachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("External payment request was interrupted", e);
        }
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

    private int parseIntegerResponse(String response) {
        try {
            return Integer.parseInt(response.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "External payment service returned invalid response: " + response,
                    e
            );
        }
    }

    private void validatePaymentDetails(PaymentDetails paymentDetails) {
        if (paymentDetails == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        if (isBlank(paymentDetails.cardNumber)) {
            throw new IllegalArgumentException("Card number is required");
        }

        if (isBlank(paymentDetails.month)) {
            throw new IllegalArgumentException("Card expiration month is required");
        }

        if (isBlank(paymentDetails.year)) {
            throw new IllegalArgumentException("Card expiration year is required");
        }

        if (isBlank(paymentDetails.holder)) {
            throw new IllegalArgumentException("Card holder is required");
        }

        if (isBlank(paymentDetails.cvv)) {
            throw new IllegalArgumentException("CVV is required");
        }

        if (isBlank(paymentDetails.id)) {
            throw new IllegalArgumentException("Card holder ID is required");
        }
    }

    private String normalizeCurrency(String currency) {
        if (isBlank(currency)) {
            return "ILS";
        }

        return currency.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}