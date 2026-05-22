package org.example.ApplicationLayer;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.IPurchaseRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

public class ActivePurchaseCleaner extends Thread
{
    private final PurchaseService purchaseService;
    private final IPurchaseRepository purchaseRepository;
    private final INotifier notifier;

    private static final Logger logger =
            Logger.getLogger(ActivePurchaseCleaner.class.getName());

    public ActivePurchaseCleaner(PurchaseService purchaseService, IPurchaseRepository purchaseRepository, INotifier notifier) {

        this.purchaseService = purchaseService;
        this.purchaseRepository = purchaseRepository;
        this.notifier = notifier;
    }

    @Override
    public void run() {
        logger.info("ActivePurchaseCleaner started");
        while (true) {

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

                Thread.sleep(1000); // כל דקה

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

