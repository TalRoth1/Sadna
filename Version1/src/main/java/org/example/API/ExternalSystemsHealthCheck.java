package org.example.API;

import org.example.InfrastructureLayer.ExternalPaymentGateway;
import org.example.InfrastructureLayer.ExternalTicketingGateway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Startup connectivity check for the external WSEP systems.
 *
 * <p>On boot it performs the WSEP {@code action_type=handshake} against the
 * payment and ticketing systems and logs the outcome, so operators can see at
 * a glance whether the live clearing / supply systems are reachable.
 *
 * <p>Self-gating: a handshake only runs for a system whose configured
 * {@code default-provider} is {@code EXTERNAL}. On the {@code dev} profile both
 * default to {@code SIMULATED}, so this check makes no network calls there.
 *
 * <p>Best-effort and non-blocking: it runs on a daemon thread (a slow or
 * unreachable WSEP must never delay application startup) and never throws.
 */
@Component
public class ExternalSystemsHealthCheck implements CommandLineRunner {

    private static final Logger logger =
            Logger.getLogger(ExternalSystemsHealthCheck.class.getName());

    private final BackendConfigProperties config;
    private final ExternalPaymentGateway externalPaymentGateway;
    private final ExternalTicketingGateway externalTicketingGateway;

    public ExternalSystemsHealthCheck(BackendConfigProperties config,
                                      ExternalPaymentGateway externalPaymentGateway,
                                      ExternalTicketingGateway externalTicketingGateway) {
        this.config = config;
        this.externalPaymentGateway = externalPaymentGateway;
        this.externalTicketingGateway = externalTicketingGateway;
    }

    @Override
    public void run(String... args) {
        Thread worker = new Thread(this::performHandshakes, "external-systems-handshake");
        worker.setDaemon(true);
        worker.start();
    }

    private void performHandshakes() {
        if (ExternalPaymentGateway.PROVIDER_ID.equalsIgnoreCase(
                config.getPayment().getDefaultProvider())) {
            handshake("payment", externalPaymentGateway::handshake);
        }

        if (ExternalTicketingGateway.PROVIDER_ID.equalsIgnoreCase(
                config.getTicketing().getDefaultProvider())) {
            handshake("ticketing", externalTicketingGateway::handshake);
        }
    }

    private void handshake(String system, Handshake handshake) {
        try {
            boolean ok = handshake.run();
            if (ok) {
                logger.info("[ExternalSystemsHealthCheck] " + system
                        + " WSEP handshake OK — external system reachable.");
            } else {
                logger.warning("[ExternalSystemsHealthCheck] " + system
                        + " WSEP handshake FAILED — external system responded but not with OK.");
            }
        } catch (RuntimeException e) {
            logger.warning("[ExternalSystemsHealthCheck] " + system
                    + " WSEP handshake could not reach the external system: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Handshake {
        boolean run();
    }
}
