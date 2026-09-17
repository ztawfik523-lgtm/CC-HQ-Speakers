package com.tom.hqspeaker.client;

/**
 * Pure coordination state for authoritative decoder revisions and local worker epochs.
 *
 * <p>The server revision answers "does this STATE require fresh codec state?". The local epoch answers
 * "is this worker still allowed to report into the current client session?". They are deliberately separate.</p>
 */
public final class FiniteDecodeCoordinator {
    public enum StateDecision { STALE, KEEP, RESTART, HIBERNATE }

    private long serverRevision;
    private long localEpoch;

    public StateDecision observeState(long incomingRevision, boolean localDecoderUsable,
                                      boolean localExhausted, boolean hibernate) {
        if (incomingRevision <= 0L) throw new IllegalArgumentException("decode revision must be positive");
        if (incomingRevision < serverRevision) return StateDecision.STALE;

        boolean revisionChanged = incomingRevision > serverRevision;
        serverRevision = incomingRevision;

        if (hibernate) return StateDecision.HIBERNATE;
        if (revisionChanged) return StateDecision.RESTART;
        if (localExhausted) return StateDecision.KEEP;
        return localDecoderUsable ? StateDecision.KEEP : StateDecision.RESTART;
    }

    /** Invalidate the current worker identity before any cancellation primitive is triggered. */
    public long invalidateLocalEpoch() {
        if (localEpoch == Long.MAX_VALUE) throw new IllegalStateException("local decode epoch exhausted");
        return ++localEpoch;
    }

    public long currentLocalEpoch() { return localEpoch; }
    public boolean isCurrentLocalEpoch(long epoch) { return epoch == localEpoch; }
    public long serverRevision() { return serverRevision; }
}
