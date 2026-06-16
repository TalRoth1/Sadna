package org.example.AdditionalTests;

import org.example.InfrastructureLayer.SimulatedTicketingGateway;
import org.junit.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class SimulatedTicketingGatewayAdditionalTests {

    @Test
    public void providerIdAndDefaultIssueTickets_succeedsWithDeterministicReference() {
        SimulatedTicketingGateway gateway = new SimulatedTicketingGateway();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        assertEquals(SimulatedTicketingGateway.PROVIDER_ID, gateway.providerId());
        assertEquals(SimulatedTicketingGateway.Outcome.SUCCEED, gateway.peekNextOutcome());

        String reference = gateway.issueTickets(userId, eventId, Set.of(ticketId));

        assertEquals("SIM-TICKET-" + eventId + "-" + userId, reference);
        assertEquals(SimulatedTicketingGateway.Outcome.SUCCEED, gateway.peekNextOutcome());
    }

    @Test
    public void failNextIssue_isOneShotAndThenResetsToSuccess() {
        SimulatedTicketingGateway gateway = new SimulatedTicketingGateway();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        gateway.failNextIssue();
        assertEquals(SimulatedTicketingGateway.Outcome.FAIL, gateway.peekNextOutcome());

        SimulatedTicketingGateway.TicketingFailedException thrown = assertThrows(
                SimulatedTicketingGateway.TicketingFailedException.class,
                () -> gateway.issueTickets(userId, eventId, Set.of(UUID.randomUUID()))
        );

        assertTrue(thrown.getMessage().contains(eventId.toString()));
        assertEquals(SimulatedTicketingGateway.Outcome.SUCCEED, gateway.peekNextOutcome());

        assertTrue(gateway.issueTickets(userId, eventId, Set.of(UUID.randomUUID()))
                .startsWith("SIM-TICKET-"));
    }

    @Test
    public void succeedNextIssue_overridesPendingFailure() {
        SimulatedTicketingGateway gateway = new SimulatedTicketingGateway();

        gateway.failNextIssue();
        gateway.succeedNextIssue();

        assertEquals(SimulatedTicketingGateway.Outcome.SUCCEED, gateway.peekNextOutcome());
        assertNotNull(gateway.issueTickets(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID())));
    }
}
