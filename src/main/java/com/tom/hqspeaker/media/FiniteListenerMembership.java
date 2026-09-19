package com.tom.hqspeaker.media;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tracks which players currently own a client session for one active finite playback. */
public final class FiniteListenerMembership {
    public record Delta(Set<UUID> joined, Set<UUID> left) {}

    private final Set<UUID> admitted = new LinkedHashSet<>();

    public Delta plan(Set<UUID> relevant) {
        Objects.requireNonNull(relevant, "relevant");

        Set<UUID> joined = new LinkedHashSet<>(relevant);
        joined.removeAll(admitted);

        Set<UUID> left = new LinkedHashSet<>(admitted);
        left.removeAll(relevant);

        return new Delta(Set.copyOf(joined), Set.copyOf(left));
    }

    public boolean admit(UUID playerId) {
        return playerId != null && admitted.add(playerId);
    }

    public boolean contains(UUID playerId) {
        return playerId != null && admitted.contains(playerId);
    }

    public boolean remove(UUID playerId) {
        return playerId != null && admitted.remove(playerId);
    }

    public Set<UUID> snapshot() {
        return Set.copyOf(admitted);
    }

    public boolean isEmpty() {
        return admitted.isEmpty();
    }

    public void clear() {
        admitted.clear();
    }
}
