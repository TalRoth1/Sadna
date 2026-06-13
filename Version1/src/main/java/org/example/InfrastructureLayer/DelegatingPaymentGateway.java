package org.example.InfrastructureLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentProvider;
import org.example.ApplicationLayer.PaymentResult;

/**
 * Multi-provider entry point for payment clearing (general requirement I.3:
 * "support more than one clearing service").
 *
 * <p>This is the only {@link IPaymentGateway} the domain layer sees. It keeps
 * a registry of every available {@link PaymentProvider} keyed by its
 * {@link PaymentProvider#providerId()} and routes each charge / refund to the
 * provider chosen by {@link #resolveProvider()}.
 *
 * <p>Today {@code resolveProvider()} returns the provider named in
 * configuration ({@code backend.payment.default-provider}); this is the single
 * seam where per-company / per-currency provider selection can be added later
 * without the domain layer ever learning about concrete providers. Mirrors
 * {@link DelegatingTicketingGateway} for ticket issuance.
 */
public class DelegatingPaymentGateway implements IPaymentGateway {

    private static final Logger logger =
            Logger.getLogger(DelegatingPaymentGateway.class.getName());

    private final Map<String, PaymentProvider> providersById;
    private final String defaultProviderId;

    public DelegatingPaymentGateway(List<PaymentProvider> providers, String defaultProviderId) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one payment provider is required");
        }

        Map<String, PaymentProvider> registry = new LinkedHashMap<>();
        for (PaymentProvider provider : providers) {
            PaymentProvider previous = registry.put(provider.providerId(), provider);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate payment provider id: " + provider.providerId());
            }
        }

        if (defaultProviderId == null || !registry.containsKey(defaultProviderId)) {
            throw new IllegalArgumentException(
                    "Unknown default payment provider '" + defaultProviderId
                            + "'. Registered providers: " + registry.keySet());
        }

        this.providersById = registry;
        this.defaultProviderId = defaultProviderId;

        logger.info("[DelegatingPaymentGateway] registered providers=" + registry.keySet()
                + ", default=" + defaultProviderId);
    }

    @Override
    public PaymentResult pay(UUID userID, float amount, PaymentDetails paymentDetails) {
        return resolveProvider().pay(userID, amount, paymentDetails);
    }

    @Override
    public boolean refund(int transactionId) {
        return resolveProvider().refund(transactionId);
    }

    /**
     * Provider selection seam. For now returns the configured default; extend
     * here to pick a provider per company / currency.
     */
    private PaymentProvider resolveProvider() {
        return providersById.get(defaultProviderId);
    }

    /** Diagnostics — the set of registered provider ids. */
    public java.util.Set<String> registeredProviders() {
        return providersById.keySet();
    }
}
