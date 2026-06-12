package org.example.ApplicationLayer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;

public class ActivePurchaseCleaner extends Thread {
    private final PurchaseService purchaseService;
    private final IPurchaseRepository purchaseRepository;
    private final INotifier notifier;
    private final long sweepIntervalMs;
    private final long warningBeforeExpirySeconds;

    private final Set<UUID> warnedPurchases = ConcurrentHashMap.newKeySet();

    private static final Logger logger =
            Logger.getLogger(ActivePurchaseCleaner.class.getName());

    public ActivePurchaseCleaner(
            PurchaseService purchaseService,
            IPurchaseRepository purchaseRepository,
            INotifier notifier,
            Duration sweepInterval,
            long warningBeforeExpirySeconds
    ) {
        if (sweepInterval == null) {
            throw new IllegalArgumentException("Sweep interval must not be null");
        }
        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.notifier = notifier;
        this.sweepIntervalMs = sweepInterval.toMillis();
        this.warningBeforeExpirySeconds = warningBeforeExpirySeconds;

        setDaemon(true);
        setName("active-purchase-cleaner");
    }

    @Override
    public void run() {
        logger.info("ActivePurchaseCleaner started");

        while (!Thread.currentThread().isInterrupted()) {
            List<ActivePurchase> purchases = purchaseRepository.findAll();
            LocalDateTime now = LocalDateTime.now();

            for (ActivePurchase purchase : purchases) {
                UUID purchaseId = purchase.getActivePurchaseId();

                if (purchase.isExpired(now)) {
                    try {
                        logger.log(Level.INFO, "Cleaning expired active purchase: {0}", purchaseId);
                        purchaseService.cancelActivePurchase(purchaseId);
                    } catch (Exception e) {
                        logger.log(
                                Level.WARNING,
                                "Failed to cancel expired active purchase {0}: {1}",
                                new Object[]{purchaseId, e.getMessage()}
                        );
                    } finally {
                        warnedPurchases.remove(purchaseId);
                    }

                    continue;
                }

                long secondsUntilExpiry =
                        ChronoUnit.SECONDS.between(now, purchase.getEndTime());

                if (
                        secondsUntilExpiry > 0
                                && secondsUntilExpiry <= warningBeforeExpirySeconds
                                && warnedPurchases.add(purchaseId)
                ) {
                    notifier.notifyUser(
                            purchase.getUserID(),
                            "Active Order is about to be canceled"
                    );
                }
            }

            LockSupport.parkNanos(sweepIntervalMs * 1_000_000L);
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        logger.info("ActivePurchaseCleaner stopped");
    }
}