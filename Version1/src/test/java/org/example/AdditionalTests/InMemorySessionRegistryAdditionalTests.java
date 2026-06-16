package org.example.AdditionalTests;

import org.example.ApplicationLayer.ActiveSession;
import org.example.InfrastructureLayer.InMemorySessionRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class InMemorySessionRegistryAdditionalTests {

    @Test
    public void replaceSession_validatesArgumentsAndReturnsPreviousSession() {
        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        UUID userId = UUID.randomUUID();
        ActiveSession first = mock(ActiveSession.class);
        ActiveSession second = mock(ActiveSession.class);

        assertThrows(IllegalArgumentException.class, () -> registry.replaceSession(null, first));
        assertThrows(IllegalArgumentException.class, () -> registry.replaceSession(userId, null));

        assertEquals(Optional.empty(), registry.replaceSession(userId, first));
        assertEquals(Optional.of(first), registry.replaceSession(userId, second));
        assertEquals(Optional.of(second), registry.replaceSession(userId, first));
    }

    @Test
    public void replaceSession_keepsDifferentUsersIndependent() {
        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        ActiveSession firstSession = mock(ActiveSession.class);
        ActiveSession secondSession = mock(ActiveSession.class);

        assertEquals(Optional.empty(), registry.replaceSession(firstUser, firstSession));
        assertEquals(Optional.empty(), registry.replaceSession(secondUser, secondSession));
        assertEquals(Optional.of(firstSession), registry.replaceSession(firstUser, mock(ActiveSession.class)));
        assertEquals(Optional.of(secondSession), registry.replaceSession(secondUser, mock(ActiveSession.class)));
    }

    @Test
    public void replaceSession_concurrentReplacementsHaveExactlyOneEmptyPreviousSession() throws Exception {
        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        UUID userId = UUID.randomUUID();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger emptyPreviousCount = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    if (registry.replaceSession(userId, mock(ActiveSession.class)).isEmpty()) {
                        emptyPreviousCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();

        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(failures.toString(), failures.isEmpty());
        assertEquals(1, emptyPreviousCount.get());
    }
}
