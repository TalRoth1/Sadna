package org.example.AdditionalTests;

import org.example.ApplicationLayer.LotteryScheduler;
import org.example.ApplicationLayer.PurchaseService;
import org.example.DomainLayer.ILotteryRepository;
import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LotterySchedulerAdditionalTests {

    @Test
    public void constructor_setsDaemonNameAndIntervalCanBeUsedByRunLoop() throws Exception {
        PurchaseService purchaseService = mock(PurchaseService.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);
        when(lotteryRepository.findEventIdsReadyForDraw(any(LocalDateTime.class))).thenReturn(List.of());

        LotteryScheduler scheduler = new LotteryScheduler(
                purchaseService,
                lotteryRepository,
                Duration.ofMillis(5)
        );

        assertTrue(scheduler.isDaemon());
        assertEquals("lottery-scheduler", scheduler.getName());

        scheduler.start();
        verify(lotteryRepository, timeout(500).atLeastOnce()).findEventIdsReadyForDraw(any(LocalDateTime.class));
        scheduler.interrupt();
        scheduler.join(1_000);
        assertFalse(scheduler.isAlive());
    }

    @Test
    public void drawReadyLotteries_drawsEveryReadyEventAndKeepsGoingAfterFailure() throws Exception {
        PurchaseService purchaseService = mock(PurchaseService.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);
        LotteryScheduler scheduler = new LotteryScheduler(
                purchaseService,
                lotteryRepository,
                Duration.ofSeconds(1)
        );
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();

        when(lotteryRepository.findEventIdsReadyForDraw(any(LocalDateTime.class)))
                .thenReturn(List.of(firstEvent, secondEvent));
        doThrow(new RuntimeException("boom"))
                .when(purchaseService).drawLotteryForEvent(eq(secondEvent), any(LocalDateTime.class));

        invokeDrawReadyLotteries(scheduler);

        verify(purchaseService).drawLotteryForEvent(eq(firstEvent), any(LocalDateTime.class));
        verify(purchaseService).drawLotteryForEvent(eq(secondEvent), any(LocalDateTime.class));
    }

    @Test
    public void runLoopSurvivesRepositoryFailuresUntilInterrupted() throws Exception {
        PurchaseService purchaseService = mock(PurchaseService.class);
        ILotteryRepository lotteryRepository = mock(ILotteryRepository.class);
        when(lotteryRepository.findEventIdsReadyForDraw(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("repository is down"));

        LotteryScheduler scheduler = new LotteryScheduler(
                purchaseService,
                lotteryRepository,
                Duration.ofMillis(5)
        );

        scheduler.start();
        verify(lotteryRepository, timeout(500).atLeastOnce()).findEventIdsReadyForDraw(any(LocalDateTime.class));
        scheduler.interrupt();
        scheduler.join(1_000);
        assertFalse(scheduler.isAlive());
    }

    private void invokeDrawReadyLotteries(LotteryScheduler scheduler) throws Exception {
        Method method = LotteryScheduler.class.getDeclaredMethod("drawReadyLotteries");
        method.setAccessible(true);
        method.invoke(scheduler);
    }
}
