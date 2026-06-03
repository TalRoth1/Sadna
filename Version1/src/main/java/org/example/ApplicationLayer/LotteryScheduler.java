package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;

public class LotteryScheduler extends Thread {
    private static final long SWEEP_INTERVAL_MS = 5000;

    private final PurchaseDomainService purchaseDomainService;
    private final ILotteryRepository lotteryRepository;
    private final INotifier notifier;
    private final IEventRepository eventRepository;

    private static final Logger logger = Logger.getLogger(LotteryScheduler.class.getName());

    public LotteryScheduler(PurchaseDomainService purchaseDomainService,
                            ILotteryRepository lotteryRepository,
                            INotifier notifier,
                            IEventRepository eventRepository) {
        this.purchaseDomainService = purchaseDomainService;
        this.lotteryRepository = lotteryRepository;
        this.notifier = notifier;
        this.eventRepository = eventRepository;

        setDaemon(true);
        setName("lottery-scheduler");
    }

    @Override
    public void run() {
        logger.info("LotteryScheduler started");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<PuchaseLottery> lotteries = lotteryRepository.findAll();
                LocalDateTime now = LocalDateTime.now();

                for (PuchaseLottery lottery : lotteries) {
                    try {
                        if (lottery.getRegisteredUsers().isEmpty()) {
                            continue;
                        }

                        // If registration closed and winners not yet drawn -> draw
                        if ((now.isAfter(lottery.getRegistrationClose()) || now.isEqual(lottery.getRegistrationClose()))
                                && lottery.getWinnerUsers().isEmpty()) {

                            logger.info("Drawing lottery for event: " + lottery.getEventId());

                            // use 10 minutes for winner access code expiry
                            Map<String, String> winnerCodes = purchaseDomainService.drawLotteryForEvent(
                                    lottery.getEventId(),
                                    LocalDateTime.now().plusMinutes(10)
                            );

                            // Fetch refreshed lottery from repo
                            PuchaseLottery refreshed = lotteryRepository.findByEventID(lottery.getEventId());
                            Event event = eventRepository.getById(lottery.getEventId());
                            String eventName = event != null ? event.getName() : "the event";

                            // Notify winners
                            for (String winnerId : refreshed.getWinnerUsers()) {
                                String code = refreshed.getWinnerAccessCode(winnerId);
                                String msg = "You won the lottery for " + eventName + ". Access code: " + code;
                                notifier.notifyUser(winnerId, msg);
                            }

                            // Notify non-winners
                            for (String reg : refreshed.getRegisteredUsers()) {
                                if (!refreshed.isWinner(reg)) {
                                    String msg = "Lottery for " + eventName + " ended. You did not win.";
                                    notifier.notifyUser(reg, msg);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Failed handling lottery " + lottery.getLotteryId() + ": " + e.getMessage());
                    }
                }

                Thread.sleep(SWEEP_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("LotteryScheduler stopped");
    }
}
