package com.tom.hqspeaker.client;

/**
 * Pure client recovery timing shared by renderer reads and the main client tick.
 */
final class FiniteRecoveryCoordinator {
    private long starvedSinceNanos = -1L;
    private boolean awaitingState;
    private long lastReadyAttemptNanos = Long.MIN_VALUE;

    synchronized void observeRead(FinitePcmReadAdapter.State state, long nowNanos) {
        if (state == null) throw new NullPointerException("state");
        switch (state) {
            case DATA -> starvedSinceNanos = -1L;
            case SILENCE -> {
                if (starvedSinceNanos < 0L) starvedSinceNanos = nowNanos;
            }
            case EOF, CANCELLED -> starvedSinceNanos = -1L;
        }
    }

    synchronized boolean longStarved(long nowNanos, long thresholdNanos) {
        if (thresholdNanos <= 0L) throw new IllegalArgumentException("thresholdNanos must be positive");
        return starvedSinceNanos >= 0L
            && nowNanos >= starvedSinceNanos
            && nowNanos - starvedSinceNanos >= thresholdNanos;
    }

    synchronized void beginRejoin() {
        starvedSinceNanos = -1L;
        if (awaitingState) return;
        awaitingState = true;
        lastReadyAttemptNanos = Long.MIN_VALUE;
    }

    synchronized void stateReceived() {
        starvedSinceNanos = -1L;
        awaitingState = false;
        lastReadyAttemptNanos = Long.MIN_VALUE;
    }

    synchronized boolean awaitingState() {
        return awaitingState;
    }

    synchronized boolean readyDue(long nowNanos, long retryNanos) {
        if (retryNanos <= 0L) throw new IllegalArgumentException("retryNanos must be positive");
        if (!awaitingState) return false;
        if (lastReadyAttemptNanos == Long.MIN_VALUE) return true;
        return nowNanos >= lastReadyAttemptNanos && nowNanos - lastReadyAttemptNanos >= retryNanos;
    }

    synchronized void markReadyAttempt(long nowNanos) {
        if (awaitingState) lastReadyAttemptNanos = nowNanos;
    }
}
