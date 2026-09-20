package com.tom.hqspeaker.peripheral;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedMultiLockTest {
    @Test
    void actionRunsWithEveryTargetReserved() throws Exception {
        Object commandA = new Object();
        Object stateA = new Object();
        Object commandB = new Object();
        Object stateB = new Object();

        OrderedMultiLock.Target a = new OrderedMultiLock.Target(10, commandA, stateA);
        OrderedMultiLock.Target b = new OrderedMultiLock.Target(20, commandB, stateB);

        boolean result = OrderedMultiLock.run(List.of(b, a), () -> {
            assertTrue(Thread.holdsLock(commandA));
            assertTrue(Thread.holdsLock(stateA));
            assertTrue(Thread.holdsLock(commandB));
            assertTrue(Thread.holdsLock(stateB));
            return true;
        });

        assertTrue(result);
    }

    @Test
    void oppositeInputOrdersCannotDeadlock() {
        Object commandA = new Object();
        Object stateA = new Object();
        Object commandB = new Object();
        Object stateB = new Object();

        OrderedMultiLock.Target a = new OrderedMultiLock.Target(1, commandA, stateA);
        OrderedMultiLock.Target b = new OrderedMultiLock.Target(2, commandB, stateB);

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> first = executor.submit(() -> {
                    for (int i = 0; i < 2_000; i++) {
                        OrderedMultiLock.run(List.of(a, b), () -> null);
                    }
                    return null;
                });
                Future<?> second = executor.submit(() -> {
                    for (int i = 0; i < 2_000; i++) {
                        OrderedMultiLock.run(List.of(b, a), () -> null);
                    }
                    return null;
                });
                first.get();
                second.get();
            }
        });
    }

    @Test
    void overlappingGroupsSerializeAtSharedTarget() throws Exception {
        Object commandA = new Object();
        Object stateA = new Object();
        Object commandB = new Object();
        Object stateB = new Object();
        Object commandC = new Object();
        Object stateC = new Object();

        OrderedMultiLock.Target a = new OrderedMultiLock.Target(1, commandA, stateA);
        OrderedMultiLock.Target b = new OrderedMultiLock.Target(2, commandB, stateB);
        OrderedMultiLock.Target c = new OrderedMultiLock.Target(3, commandC, stateC);

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = executor.submit(() -> OrderedMultiLock.run(List.of(a, b), () -> {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
                return null;
            }));

            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> OrderedMultiLock.run(List.of(c, b), () -> {
                secondEntered.countDown();
                return null;
            }));

            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void duplicateOrderIsRejected() {
        OrderedMultiLock.Target a = new OrderedMultiLock.Target(5, new Object(), new Object());
        OrderedMultiLock.Target b = new OrderedMultiLock.Target(5, new Object(), new Object());
        assertThrows(IllegalArgumentException.class, () ->
            OrderedMultiLock.run(List.of(a, b), () -> null));
    }

    @Test
    void enteringWithATargetLockAlreadyHeldIsRejected() {
        Object command = new Object();
        OrderedMultiLock.Target target = new OrderedMultiLock.Target(1, command, new Object());
        synchronized (command) {
            assertThrows(IllegalStateException.class, () ->
                OrderedMultiLock.run(List.of(target), () -> null));
        }
    }
}
