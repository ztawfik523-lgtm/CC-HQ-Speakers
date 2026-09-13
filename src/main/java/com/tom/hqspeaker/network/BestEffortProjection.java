package com.tom.hqspeaker.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Runs client/network projection work without allowing a RuntimeException to escape into canonical server truth. */
public final class BestEffortProjection {
    private BestEffortProjection() {}

    public static boolean run(Runnable action, Consumer<RuntimeException> onFailure) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(onFailure, "onFailure");
        try {
            action.run();
            return true;
        } catch (RuntimeException failure) {
            try {
                onFailure.accept(failure);
            } catch (RuntimeException ignored) {
                // Diagnostics must not defeat the isolation this helper exists to provide.
            }
            return false;
        }
    }
}
