package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.DomainLayer.ILotteryRepository;

public class LotteryScheduler extends Thread {
    private static final long SWEEP_INTERVAL_MS = 1000;

    private final PurchaseService purchaseService;
    private final ILotteryRepository lotteryRepository;

    private static final Logger logger = Logger.getLogger(LotteryScheduler.class.getName());

    public LotteryScheduler(PurchaseService purchaseService,
                            ILotteryRepository lotteryRepository) {
        this.purchaseService = purchaseService;
        this.lotteryRepository = lotteryRepository;

        setDaemon(true);
        setName("lottery-scheduler");
    }

    @Override
    public void run() {
        logger.info("LotteryScheduler started");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                drawReadyLotteries();
                Thread.sleep(SWEEP_INTERVAL_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;

            } catch (Exception e) {
                logger.log(Level.WARNING, "LotteryScheduler sweep failed: " + e.getMessage(), e);

                try {
                    Thread.sleep(SWEEP_INTERVAL_MS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("LotteryScheduler stopped");
    }

    private void drawReadyLotteries() {
        LocalDateTime now = LocalDateTime.now();

        List<UUID> eventIdsReadyForDraw =
                lotteryRepository.findEventIdsReadyForDraw(now);

        for (UUID eventId : eventIdsReadyForDraw) {
            try {
                LocalDateTime codeExpiry = now.plusHours(24);

                purchaseService.drawLotteryForEvent(eventId, codeExpiry);

                logger.info("Automatic lottery draw completed for event: " + eventId);

            } catch (Exception e) {
                logger.log(
                        Level.WARNING,
                        "Automatic lottery draw failed for event "
                                + eventId
                                + ": "
                                + e.getMessage(),
                        e
                );
            }
        }
    }
}