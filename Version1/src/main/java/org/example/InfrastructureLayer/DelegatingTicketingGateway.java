package org.example.InfrastructureLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.TicketingProvider;

/**
 * Multi-provider entry point for ticket issuance (general requirement I.4:
 * "support more than one supply service").
 *
 * <p>This is the only {@link ITicketingGateway} the domain layer sees. It
 * keeps a registry of every available {@link TicketingProvider} keyed by its
 * {@link TicketingProvider#providerId()} and routes each issuance to the
 * provider chosen by {@link #resolveProvider()}.
 *
 * <p>Today {@code resolveProvider()} returns the provider named in
 * configuration ({@code backend.ticketing.default-provider}); this is the
 * single seam where per-event / per-company provider selection can be added
 * later without the domain layer ever learning about concrete providers.
 */
public class DelegatingTicketingGateway implements ITicketingGateway {

    private static final Logger logger =
            Logger.getLogger(DelegatingTicketingGateway.class.getName());

    private final Map<String, TicketingProvider> providersById;
    private final String defaultProviderId;

    public DelegatingTicketingGateway(List<TicketingProvider> providers, String defaultProviderId) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one ticketing provider is required");
        }

        Map<String, TicketingProvider> registry = new LinkedHashMap<>();
        for (TicketingProvider provider : providers) {
            TicketingProvider previous = registry.put(provider.providerId(), provider);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate ticketing provider id: " + provider.providerId());
            }
        }

        if (defaultProviderId == null || !registry.containsKey(defaultProviderId)) {
            throw new IllegalArgumentException(
                    "Unknown default ticketing provider '" + defaultProviderId
                            + "'. Registered providers: " + registry.keySet());
        }

        this.providersById = registry;
        this.defaultProviderId = defaultProviderId;

        logger.info("[DelegatingTicketingGateway] registered providers=" + registry.keySet()
                + ", default=" + defaultProviderId);
    }

    @Override
    public String issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
        return resolveProvider().issueTickets(userId, eventId, ticketIds);
    }

    /**
     * Cancel already-issued ticket codes via the active provider
     * (system-initiated rollback). Best-effort: each cancellation failure is
     * logged and swallowed so it can never mask the original purchase failure
     * that triggered the rollback.
     */
    @Override
    public void cancelTickets(List<String> ticketReferences) {
        if (ticketReferences == null || ticketReferences.isEmpty()) {
            return;
        }

        TicketingProvider provider = resolveProvider();
        for (String reference : ticketReferences) {
            if (reference == null || reference.isBlank()) {
                continue;
            }
            try {
                boolean cancelled = provider.cancelTicket(reference);
                logger.info("[DelegatingTicketingGateway] cancelTicket ref=" + reference
                        + " provider=" + provider.providerId() + " cancelled=" + cancelled);
            } catch (RuntimeException cancelError) {
                logger.log(Level.WARNING,
                        "[DelegatingTicketingGateway] cancelTicket failed for ref=" + reference
                                + " provider=" + provider.providerId(),
                        cancelError);
            }
        }
    }

    /**
     * Provider selection seam. For now returns the configured default;
     * extend here to pick a provider per event / company.
     */
    private TicketingProvider resolveProvider() {
        return providersById.get(defaultProviderId);
    }

    /** Diagnostics — the set of registered provider ids. */
    public Set<String> registeredProviders() {
        return providersById.keySet();
    }
}
