package org.example.ApplicationLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.IPurchaseRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

/**
 * Background sweep that releases tickets from any {@link ActivePurchase}
 * whose 10-minute reservation window has lapsed (spec invariant: a
 * reservation never lives longer than the configured wait time).
 *
 * Wired into Spring as a daemon bean in {@code BeanConfig} with
 * {@code initMethod="start"} / {@code destroyMethod="interrupt"} so the
 * sweep starts automatically on application boot and shuts down with the
 * context. Without that wiring this thread never runs and expired
 * reservations leak forever in the in-memory repo.
 */
public class ActivePurchaseCleaner extends Thread
{
    /** How often the sweep iterates. In-memory check, so 1 s is cheap. */
    private static final long SWEEP_INTERVAL_MS = 1000;

    private final PurchaseService purchaseService;
    private final IPurchaseRepository purchaseRepository;
    private final INotifier notifier;

    private static final Logger logger =
            Logger.getLogger(ActivePurchaseCleaner.class.getName());

    public ActivePurchaseCleaner(PurchaseService purchaseService, IPurchaseRepository purchaseRepository, INotifier notifier) {

        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.notifier = notifier;

        // Daemon so the JVM can exit cleanly during Spring context shutdown.
        // Name it so it's identifiable in thread dumps / IDE consoles.
        setDaemon(true);
        setName("active-purchase-cleaner");
    }

    @Override
    public void run() {
        logger.info("ActivePurchaseCleaner started");
        while (!Thread.currentThread().isInterrupted()) {

            try {
                List<ActivePurchase> purchases =
                        purchaseRepository.findAll();

                LocalDateTime now = LocalDateTime.now();

                for (ActivePurchase purchase : purchases) {

                    if (purchase.isExpired(now)) {

                        try {
                            logger.info("Cleaning expired active purchase: "+ purchase.getActivePurchaseId());
                            purchaseService.cancelActivePurchase( purchase.getActivePurchaseId());
                        } catch (Exception e) {
                            logger.warning("Failed to cancel expired active purchase "+ purchase.getActivePurchaseId()+ ": "+ e.getMessage());
                        }
                    }
                    else if (ChronoUnit.MINUTES.between(purchase.getLastUpdate(), LocalDateTime.now()) <= 1)
                        notifier.notifyUser(purchase.getUserID(), "Active Order is about to be canceled");
                        
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

