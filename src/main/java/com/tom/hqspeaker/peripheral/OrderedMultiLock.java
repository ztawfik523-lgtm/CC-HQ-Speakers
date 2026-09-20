package com.tom.hqspeaker.peripheral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Acquires a target set's command locks first, then its state locks, in one stable total order.
 *
 * <p>This is intentionally tiny: it exists only to keep multi-speaker commands from interleaving halfway through
 * admission/replacement/commit, without introducing a global lock for unrelated speakers.</p>
 */
final class OrderedMultiLock {
    record Target(long order, Object commandLock, Object stateLock) {
        Target {
            Objects.requireNonNull(commandLock, "commandLock");
            Objects.requireNonNull(stateLock, "stateLock");
        }
    }

    @FunctionalInterface
    interface Operation<T, E extends Exception> {
        T run() throws E;
    }

    private OrderedMultiLock() {}

    static <T, E extends Exception> T run(List<Target> targets, Operation<T, E> operation) throws E {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(operation, "operation");

        ArrayList<Target> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparingLong(Target::order));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).order() == ordered.get(i).order()) {
                throw new IllegalArgumentException("duplicate multi-lock order");
            }
        }

        return lockCommands(ordered, 0, () -> lockStates(ordered, 0, operation));
    }

    private static <T, E extends Exception> T lockCommands(
            List<Target> targets, int index, Operation<T, E> operation) throws E {
        if (index >= targets.size()) return operation.run();
        synchronized (targets.get(index).commandLock()) {
            return lockCommands(targets, index + 1, operation);
        }
    }

    private static <T, E extends Exception> T lockStates(
            List<Target> targets, int index, Operation<T, E> operation) throws E {
        if (index >= targets.size()) return operation.run();
        synchronized (targets.get(index).stateLock()) {
            return lockStates(targets, index + 1, operation);
        }
    }
}
