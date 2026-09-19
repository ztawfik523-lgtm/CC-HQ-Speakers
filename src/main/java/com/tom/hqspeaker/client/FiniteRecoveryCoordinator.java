package com.tom.hqspeaker.client;

/**
 * Pure client recovery timing shared by renderer reads and the main client tick.
 */
final class FiniteRecoveryCoordinator {
    private boolean starving;
    private long starvedSinceNanos;
    private boolean awaitingState;
    private boolean readyAttempted;
    private long lastReadyAttemptNanos;

    synchronized void observeRead(FinitePcmReadAdapter.State state, long nowNanos) {
        if (state == null) throw new NullPointerException("state");
        switch (state) {
            case DATA -> starving = false;
            case SILENCE -> {
                if (!starving) {
                    starving = true;
                    starvedSinceNanos = nowNanos;
                }
            }
            case EOF, CANCELLED -> starving = false;
        }
    }

    synchronized boolean longStarved(long nowNanos, long thresholdNanos) {
        if (thresholdNanos <= 0L) throw new IllegalArgumentException("thresholdNanos must be positive");
        return starving && nowNanos - starvedSinceNanos >= thresholdNanos;
    }

    synchronized void beginRejoin() {
        starving = false;
        if (awaitingState) return;
        awaitingState = true;
        readyAttempted = false;
    }

    synchronized void stateReceived() {
        starving = false;
        awaitingState = false;
        readyAttempted = false;
    }

    synchronized boolean awaitingState() {
        return awaitingState;
    }

    synchronized boolean readyDue(long nowNanos, long retryNanos) {
        if (retryNanos <= 0L) throw new IllegalArgumentException("retryNanos must be positive");
        if (!awaitingState) return false;
        return !readyAttempted || nowNanos - lastReadyAttemptNanos >= retryNanos;
    }

    synchronized void markReadyAttempt(long nowNanos) {
        if (!awaitingState) return;
        readyAttempted = true;
        lastReadyAttemptNanos = nowNanos;
    }
}
