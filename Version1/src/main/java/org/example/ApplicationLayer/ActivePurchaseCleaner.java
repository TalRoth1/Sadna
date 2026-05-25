package org.example.ApplicationLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.IPurchaseRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ActivePurchaseCleaner extends Thread {
    private static final long SWEEP_INTERVAL_MS = 1000;
    private static final long WARNING_BEFORE_EXPIRY_SECONDS = 60;

    private final PurchaseService purchaseService;
    private final IPurchaseRepository purchaseRepository;
    private final INotifier notifier;

    private final Set<UUID> warnedPurchases = ConcurrentHashMap.newKeySet();

    private static final Logger logger =
            Logger.getLogger(ActivePurchaseCleaner.class.getName());

    public ActivePurchaseCleaner(
            PurchaseService purchaseService,
            IPurchaseRepository purchaseRepository,
            INotifier notifier
    ) {
        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.notifier = notifier;

        setDaemon(true);
        setName("active-purchase-cleaner");
    }

    @Override
    public void run() {
        logger.info("ActivePurchaseCleaner started");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<ActivePurchase> purchases = purchaseRepository.findAll();
                LocalDateTime now = LocalDateTime.now();

                for (ActivePurchase purchase : purchases) {
                    UUID purchaseId = purchase.getActivePurchaseId();

                    if (purchase.isExpired(now)) {
                        try {
                            logger.info("Cleaning expired active purchase: " + purchaseId);
                            purchaseService.cancelActivePurchase(purchaseId);
                        } catch (Exception e) {
                            logger.warning(
                                    "Failed to cancel expired active purchase "
                                            + purchaseId + ": " + e.getMessage()
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
                                    && secondsUntilExpiry <= WARNING_BEFORE_EXPIRY_SECONDS
                                    && warnedPurchases.add(purchaseId)
                    ) {
                        notifier.notifyUser(
                                purchase.getUserID(),
                                "Active Order is about to be canceled"
                        );
                    }
                }

                Thread.sleep(SWEEP_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("ActivePurchaseCleaner stopped");
    }
}