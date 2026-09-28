package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.diagnostics.HQDiagnostics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-source HQ acoustic state layered on top of Sound Physics Remastered.
 *
 * <p>SPR keeps authority over the complete room/reverb/reflection calculation. This class only supplies
 * the runtime-approved progressive direct pair and smoothing state for bound HQ sources.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HQSoundPhysicsAcousticsClient {
    private static final ConcurrentHashMap<UUID, SourceState> BY_IDENTITY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SourceState> BY_OPENAL_SOURCE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_LIFETIME = new AtomicLong();
    private static final AtomicBoolean CONFIG_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean OCCLUSION_WARNING_LOGGED = new AtomicBoolean();

    private static volatile SprConfigAccess configAccess;
    private static volatile boolean configLookupAttempted;
    private static volatile SprConfigSnapshot cachedConfig;
    private static volatile long cachedConfigNanos;
    private static final long CONFIG_CACHE_NANOS = 1_000_000_000L;

    private HQSoundPhysicsAcousticsClient() {}

    @FunctionalInterface
    public interface OcclusionSampler {
        double sample(
            double sourceX, double sourceY, double sourceZ,
            double listenerX, double listenerY, double listenerZ
        );
    }

    public record AdjustedEnvironment(
        float sendGain0, float sendGain1, float sendGain2, float sendGain3,
        float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
        float directCutoff, float directGain
    ) {}

    static void attach(UUID source, int openAlSource) {
        if (source == null || openAlSource <= 0) return;

        SourceState next = new SourceState(source, openAlSource, NEXT_LIFETIME.incrementAndGet());

        SourceState oldIdentity = BY_IDENTITY.put(source, next);
        if (oldIdentity != null) BY_OPENAL_SOURCE.remove(oldIdentity.openAlSource, oldIdentity);

        SourceState oldSource = BY_OPENAL_SOURCE.put(openAlSource, next);
        if (oldSource != null && oldSource != next) BY_IDENTITY.remove(oldSource.source, oldSource);
    }

    static void detach(UUID source) {
        if (source == null) return;
        SourceState removed = BY_IDENTITY.remove(source);
        if (removed != null) BY_OPENAL_SOURCE.remove(removed.openAlSource, removed);
    }

    static void clear() {
        BY_IDENTITY.clear();
        BY_OPENAL_SOURCE.clear();
    }

    /**
     * Returns null when this is not a currently-bound HQ source or when the optional SPR config bridge is unavailable.
     * In either case the caller leaves SPR's original environment arguments untouched.
     */
    public static AdjustedEnvironment adjustEnvironment(
        int openAlSource,
        float sendGain0, float sendGain1, float sendGain2, float sendGain3,
        float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
        float nativeDirectCutoff, float nativeDirectGain,
        OcclusionSampler sampler
    ) {
        SourceState state = BY_OPENAL_SOURCE.get(openAlSource);
        if (state == null || BY_IDENTITY.get(state.source) != state) return null;

        HQSoundPhysicsRefreshClient.AcousticSnapshot position =
            HQSoundPhysicsRefreshClient.acousticSnapshot(openAlSource);
        if (position == null) return null;

        SprConfigSnapshot config = currentSprConfig();
        if (config == null) return null;

        synchronized (state) {
            float targetDirectCutoff = nativeDirectCutoff;
            float targetDirectGain = nativeDirectGain;

            if (config.strictOcclusion) {
                state.progressive.reset();
            } else {
                try {
                    HQProgressiveOcclusionModel.Result direct = state.progressive.evaluate(
                        (sx, sy, sz, lx, ly, lz) -> sampler.sample(sx, sy, sz, lx, ly, lz),
                        position.sourceX(), position.sourceY(), position.sourceZ(),
                        position.listenerX(), position.listenerY(), position.listenerZ(),
                        config.blockAbsorption,
                        config.maxOcclusion);
                    state.rawOcclusion = direct.rawOcclusion();
                    targetDirectCutoff = direct.directCutoff();
                    targetDirectGain = direct.directGain();
                    HQDiagnostics.soundPhysicsProgressive(
                        state.source, direct.rawOcclusion(), direct.sampledPaths(), direct.fullRefresh());
                } catch (RuntimeException failure) {
                    state.progressive.reset();
                    if (OCCLUSION_WARNING_LOGGED.compareAndSet(false, true)) {
                        HQSpeakerMod.warn("HQ progressive SPR occlusion failed; falling back to native direct occlusion: "
                            + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
                    }
                }
            }

            HQEnvironmentSmoother.Environment smoothed = state.smoother.update(
                new float[]{ sendGain0, sendGain1, sendGain2, sendGain3 },
                new float[]{ sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3 },
                targetDirectCutoff,
                targetDirectGain);
            float[] gains = smoothed.sendGains();
            float[] cutoffs = smoothed.sendCutoffs();

            return new AdjustedEnvironment(
                gains[0], gains[1], gains[2], gains[3],
                cutoffs[0], cutoffs[1], cutoffs[2], cutoffs[3],
                smoothed.directCutoff(), smoothed.directGain());
        }
    }

    public static boolean shouldSuppressReflectedPosition(int openAlSource) {
        SourceState state = BY_OPENAL_SOURCE.get(openAlSource);
        if (state == null || BY_IDENTITY.get(state.source) != state) return false;
        SprConfigSnapshot config = currentSprConfig();
        return config != null && !config.strictOcclusion;
    }

    public static void soundPhysicsProcessed(
        int openAlSource,
        double reflectedX, double reflectedY, double reflectedZ,
        boolean hasReflected
    ) {
        SourceState state = BY_OPENAL_SOURCE.get(openAlSource);
        if (state == null || BY_IDENTITY.get(state.source) != state) return;

        SprConfigSnapshot config = currentSprConfig();
        if (config == null || config.strictOcclusion) {
            HQSoundPhysicsRefreshClient.clearAcousticPosition(openAlSource);
            return;
        }

        HQSoundPhysicsRefreshClient.AcousticSnapshot position =
            HQSoundPhysicsRefreshClient.acousticSnapshot(openAlSource);
        if (position == null) return;

        synchronized (state) {
            HQReflectionStabilizer.Point physical = new HQReflectionStabilizer.Point(
                position.sourceX(), position.sourceY(), position.sourceZ());
            HQReflectionStabilizer.Point reflected = hasReflected
                ? new HQReflectionStabilizer.Point(reflectedX, reflectedY, reflectedZ)
                : null;
            HQReflectionStabilizer.Point stabilized =
                state.reflection.update(physical, reflected, state.rawOcclusion);
            HQSoundPhysicsRefreshClient.applyAcousticPosition(
                openAlSource, stabilized.x(), stabilized.y(), stabilized.z());
            HQDiagnostics.soundPhysicsReflectionStabilized(state.source);
        }
    }

    static double rawOcclusion(int openAlSource) {
        SourceState state = BY_OPENAL_SOURCE.get(openAlSource);
        if (state == null || BY_IDENTITY.get(state.source) != state) return 0.0;
        synchronized (state) {
            return state.rawOcclusion;
        }
    }

    private static SprConfigSnapshot currentSprConfig() {
        long now = System.nanoTime();
        SprConfigSnapshot cached = cachedConfig;
        if (cached != null && now - cachedConfigNanos < CONFIG_CACHE_NANOS) return cached;

        synchronized (HQSoundPhysicsAcousticsClient.class) {
            cached = cachedConfig;
            if (cached != null && now - cachedConfigNanos < CONFIG_CACHE_NANOS) return cached;

            SprConfigAccess access = resolveConfigAccess();
            if (access == null) return null;
            try {
                double blockAbsorption = ((Number) access.read(access.blockAbsorption)).doubleValue();
                double maxOcclusion = ((Number) access.read(access.maxOcclusion)).doubleValue();
                boolean strictOcclusion = (Boolean) access.read(access.strictOcclusion);
                if (!Double.isFinite(blockAbsorption) || !Double.isFinite(maxOcclusion)) {
                    throw new IllegalStateException("non-finite SPR acoustic config");
                }
                cached = new SprConfigSnapshot(
                    Math.max(0.0, blockAbsorption),
                    Math.max(0.0, maxOcclusion),
                    strictOcclusion);
                cachedConfig = cached;
                cachedConfigNanos = now;
                return cached;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                if (CONFIG_WARNING_LOGGED.compareAndSet(false, true)) {
                    HQSpeakerMod.warn("Could not read Sound Physics acoustic config; using native SPR environment: "
                        + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
                }
                return null;
            }
        }
    }

    private static SprConfigAccess resolveConfigAccess() {
        if (configLookupAttempted) return configAccess;
        synchronized (HQSoundPhysicsAcousticsClient.class) {
            if (configLookupAttempted) return configAccess;
            configLookupAttempted = true;
            try {
                ClassLoader loader = HQSoundPhysicsAcousticsClient.class.getClassLoader();
                Class<?> mod = Class.forName("com.sonicether.soundphysics.SoundPhysicsMod", false, loader);
                Field configField = mod.getField("CONFIG");
                Object config = configField.get(null);
                if (config == null) throw new IllegalStateException("SPR CONFIG is null");

                Field blockAbsorption = config.getClass().getField("blockAbsorption");
                Field maxOcclusion = config.getClass().getField("maxOcclusion");
                Field strictOcclusion = config.getClass().getField("strictOcclusion");
                configAccess = new SprConfigAccess(config, blockAbsorption, maxOcclusion, strictOcclusion);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                configAccess = null;
                if (CONFIG_WARNING_LOGGED.compareAndSet(false, true)) {
                    HQSpeakerMod.warn("Sound Physics acoustic config bridge unavailable; using native SPR environment: "
                        + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
                }
            }
            return configAccess;
        }
    }

    private static final class SourceState {
        final UUID source;
        final int openAlSource;
        final long lifetime;
        final HQProgressiveOcclusionModel progressive = new HQProgressiveOcclusionModel();
        final HQEnvironmentSmoother smoother = new HQEnvironmentSmoother();
        final HQReflectionStabilizer reflection = new HQReflectionStabilizer();
        double rawOcclusion;

        SourceState(UUID source, int openAlSource, long lifetime) {
            this.source = source;
            this.openAlSource = openAlSource;
            this.lifetime = lifetime;
        }
    }

    private record SprConfigSnapshot(
        double blockAbsorption,
        double maxOcclusion,
        boolean strictOcclusion
    ) {}

    private record SprConfigAccess(
        Object config,
        Field blockAbsorption,
        Field maxOcclusion,
        Field strictOcclusion
    ) {
        Object read(Field field) throws ReflectiveOperationException {
            Object entry = field.get(config);
            if (entry == null) throw new IllegalStateException("SPR config entry is null: " + field.getName());
            Method get = entry.getClass().getMethod("get");
            try {
                return get.invoke(entry);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw e;
            }
        }
    }
}
