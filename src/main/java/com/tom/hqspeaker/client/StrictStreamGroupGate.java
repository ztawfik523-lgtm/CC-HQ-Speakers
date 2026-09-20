package com.tom.hqspeaker.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Client-local strict membership gate for one grouped live stream command.
 *
 * <p>Members may join only before the command's collection deadline. Once sealed, membership never grows; a late
 * speaker must wait for a new command/group id.</p>
 */
final class StrictStreamGroupGate {
    private final UUID groupId;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private long sealTick = -1L;
    private boolean sealed;
    private boolean started;

    StrictStreamGroupGate(UUID groupId) {
        this.groupId = groupId;
    }

    synchronized boolean accept(UUID source, long requestedSealTick, long nowTick) {
        if (source == null || sealed || started) return false;
        long requested = Math.max(0L, requestedSealTick);
        if (sealTick < 0L) sealTick = requested;
        else sealTick = Math.max(sealTick, requested);
        if (nowTick >= sealTick) {
            sealed = true;
            return false;
        }
        return members.add(source) || members.contains(source);
    }

    synchronized boolean sealIfDue(long nowTick) {
        if (sealed) return false;
        if (sealTick < 0L || nowTick < sealTick) return false;
        sealed = true;
        return true;
    }

    synchronized void remove(UUID source) {
        members.remove(source);
    }

    synchronized List<UUID> members() {
        return List.copyOf(members);
    }

    synchronized boolean isSealed() { return sealed; }
    synchronized boolean isStarted() { return started; }

    synchronized void markStarted() {
        if (!sealed) throw new IllegalStateException("cannot start an unsealed stream group");
        started = true;
    }

    synchronized boolean canRemove() {
        return members.isEmpty();
    }

    UUID groupId() { return groupId; }
}
