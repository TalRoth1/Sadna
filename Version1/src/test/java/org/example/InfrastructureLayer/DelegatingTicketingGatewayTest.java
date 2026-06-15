package org.example.InfrastructureLayer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.example.ApplicationLayer.TicketingProvider;
import org.junit.jupiter.api.Test;

/**
 * Verifies the multi-provider behavior required by general requirement I.4:
 * the gateway keeps a registry of supply services and routes issuance /
 * cancellation to the provider chosen by configuration.
 */
public class DelegatingTicketingGatewayTest {

    /** Minimal in-test provider that records what it was asked to do. */
    private static final class FakeProvider implements TicketingProvider {
        private final String id;
        private final List<String> cancelled = new ArrayList<>();
        private int issueCalls = 0;

        FakeProvider(String id) {
            this.id = id;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
            issueCalls++;
            return id + "-CODE";
        }

        @Override
        public boolean cancelTicket(String ticketId) {
            cancelled.add(ticketId);
            return true;
        }
    }

    @Test
    public void issueTickets_routesToConfiguredDefaultProvider() {
        FakeProvider sim = new FakeProvider("SIMULATED");
        FakeProvider ext = new FakeProvider("EXTERNAL");

        DelegatingTicketingGateway gateway =
                new DelegatingTicketingGateway(List.of(sim, ext), "EXTERNAL");

        String ref = gateway.issueTickets(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()));

        assertEquals("EXTERNAL-CODE", ref);
        assertEquals(1, ext.issueCalls);
        assertEquals(0, sim.issueCalls);
    }

    @Test
    public void changingDefault_switchesProviderWithoutCodeChange() {
        FakeProvider sim = new FakeProvider("SIMULATED");
        FakeProvider ext = new FakeProvider("EXTERNAL");

        DelegatingTicketingGateway gateway =
                new DelegatingTicketingGateway(List.of(sim, ext), "SIMULATED");

        gateway.issueTickets(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()));

        assertEquals(1, sim.issueCalls);
        assertEquals(0, ext.issueCalls);
    }

    @Test
    public void cancelTickets_delegatesToActiveProvider() {
        FakeProvider sim = new FakeProvider("SIMULATED");
        FakeProvider ext = new FakeProvider("EXTERNAL");

        DelegatingTicketingGateway gateway =
                new DelegatingTicketingGateway(List.of(sim, ext), "SIMULATED");

        gateway.cancelTickets(List.of("REF-1", "REF-2"));

        assertEquals(List.of("REF-1", "REF-2"), sim.cancelled);
        assertTrue(ext.cancelled.isEmpty());
    }

    @Test
    public void unknownDefaultProvider_failsFastAtConstruction() {
        FakeProvider sim = new FakeProvider("SIMULATED");

        assertThrows(IllegalArgumentException.class,
                () -> new DelegatingTicketingGateway(List.of(sim), "DOES_NOT_EXIST"));
    }

    @Test
    public void cancelTickets_isBestEffort_andSwallowsProviderErrors() {
        TicketingProvider boom = new TicketingProvider() {
            @Override
            public String providerId() {
                return "BOOM";
            }

            @Override
            public String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
                return "X";
            }

            @Override
            public boolean cancelTicket(String ticketId) {
                throw new RuntimeException("provider down");
            }
        };

        DelegatingTicketingGateway gateway =
                new DelegatingTicketingGateway(List.of(boom), "BOOM");

        assertDoesNotThrow(() -> gateway.cancelTickets(List.of("REF-1")));
    }
}
