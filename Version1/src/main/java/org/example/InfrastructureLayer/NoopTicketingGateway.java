package org.example.InfrastructureLayer;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.ITicketingGateway;

/**
 * Development-only ticketing gateway. Logs the issuance request and returns;
 * a real implementation would call out to an external ticket-issuance system
 * (PDF / barcode generation, delivery, etc.).
 */
public class NoopTicketingGateway implements ITicketingGateway {

    private static final Logger logger =
            Logger.getLogger(NoopTicketingGateway.class.getName());

    @Override
    public void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
        logger.info("[NoopTicketingGateway] issueTickets user=" + userId
                + " event=" + eventId + " tickets=" + ticketIds);
    }
}
