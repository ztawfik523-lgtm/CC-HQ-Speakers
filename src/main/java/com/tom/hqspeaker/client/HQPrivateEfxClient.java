package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-OpenAL-source EFX filters for HQ sources.
 *
 * <p>Sound Physics Remastered owns the room calculation and its global auxiliary effect slots. Its stock
 * setEnvironment method also reuses one mutable set of low-pass filters for every source, which means changing
 * one long-lived HQ speaker can otherwise change another HQ speaker's filtering. HQ therefore owns only the
 * per-source filter objects and reconnects them to SPR's existing auxiliary slots on every environment apply.</p>
 */
final class HQPrivateEfxClient {
    private static final int AL_PLAYING = AL10.AL_PLAYING;
    private static final int AL_PAUSED = AL10.AL_PAUSED;
    private static final AtomicBoolean LAYOUT_WARNING_LOGGED = new AtomicBoolean();

    static final class State {
        int directFilter;
        final int[] sendFilters = new int[4];
        final int[] auxSlots = new int[4];
        int maxAuxSends;
        boolean ready;
        boolean failed;
    }

    record ApplyResult(boolean applied, int directFilter) {
        static ApplyResult nativeFallback() { return new ApplyResult(false, 0); }
    }

    private HQPrivateEfxClient() {}

    static ApplyResult apply(
        int sourceId,
        State state,
        HQEnvironmentSmoother.Environment environment,
        float airAbsorption
    ) {
        if (state == null || environment == null || sourceId <= 0 || state.failed) {
            return ApplyResult.nativeFallback();
        }

        int sourceState;
        try {
            sourceState = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        } catch (RuntimeException failure) {
            return ApplyResult.nativeFallback();
        }
        // Preserve the known-good lifecycle rule: never create private EFX before the source is actually live.
        if (sourceState != AL_PLAYING && sourceState != AL_PAUSED) {
            return ApplyResult.nativeFallback();
        }

        try {
            if (!state.ready && !create(state)) return ApplyResult.nativeFallback();
            applyEnvironment(sourceId, state, environment, airAbsorption);
            return new ApplyResult(true, state.directFilter);
        } catch (RuntimeException | LinkageError failure) {
            fail(sourceId, state, failure);
            return ApplyResult.nativeFallback();
        }
    }

    static void destroy(int sourceId, State state, boolean detachSource) {
        if (state == null) return;
        try {
            drainErrors();
            if (detachSource && sourceId > 0 && AL10.alIsSource(sourceId)) {
                AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, 0);
                for (int i = 0; i < 4; i++) {
                    int requiredSends = 4 - i;
                    if (state.maxAuxSends >= requiredSends) {
                        AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 3 - i, 0);
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The source may already have been destroyed by a sound-engine reload. Filter deletion is still attempted.
        }

        deleteFilter(state.directFilter);
        for (int filter : state.sendFilters) deleteFilter(filter);
        state.directFilter = 0;
        java.util.Arrays.fill(state.sendFilters, 0);
        java.util.Arrays.fill(state.auxSlots, 0);
        state.maxAuxSends = 0;
        state.ready = false;
        drainErrors();
    }

    private static boolean create(State state) {
        SprLayout layout = readSprLayout();
        if (layout == null || layout.maxAuxSends <= 0) return false;

        drainErrors();
        state.maxAuxSends = Math.min(4, layout.maxAuxSends);
        System.arraycopy(layout.auxSlots, 0, state.auxSlots, 0, 4);

        state.directFilter = newLowpass();
        for (int i = 0; i < 4; i++) state.sendFilters[i] = newLowpass();

        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException("OpenAL error creating HQ private EFX: " + error);
        }
        state.ready = true;
        return true;
    }

    private static int newLowpass() {
        int filter = EXTEfx.alGenFilters();
        if (filter == 0) throw new IllegalStateException("alGenFilters returned 0");
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        return filter;
    }

    private static void applyEnvironment(
        int sourceId,
        State state,
        HQEnvironmentSmoother.Environment environment,
        float airAbsorption
    ) {
        float[] gains = environment.sendGains();
        float[] cutoffs = environment.sendCutoffs();

        drainErrors();
        for (int i = 0; i < 4; i++) {
            int requiredSends = 4 - i;
            if (state.maxAuxSends < requiredSends) continue;

            int filter = state.sendFilters[i];
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, clamp01(gains[i]));
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, clamp01(cutoffs[i]));

            // Reattach every application. The old runtime-approved compat proved that mutating an attached
            // filter without reattachment is not reliable on the target OpenAL/driver path.
            AL11.alSource3i(
                sourceId,
                EXTEfx.AL_AUXILIARY_SEND_FILTER,
                state.auxSlots[i],
                3 - i,
                filter);
        }

        EXTEfx.alFilterf(state.directFilter, EXTEfx.AL_LOWPASS_GAIN, clamp01(environment.directGain()));
        EXTEfx.alFilterf(state.directFilter, EXTEfx.AL_LOWPASS_GAINHF, clamp01(environment.directCutoff()));
        AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, state.directFilter);
        AL11.alSourcef(sourceId, EXTEfx.AL_AIR_ABSORPTION_FACTOR, Math.max(0.0f, airAbsorption));

        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException("OpenAL error applying HQ private EFX: " + error);
        }
    }

    private static void fail(int sourceId, State state, Throwable failure) {
        state.failed = true;
        HQSpeakerMod.warn("HQ private per-source Sound Physics filters failed for OpenAL source " + sourceId
            + "; falling back to native SPR environment: "
            + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
        destroy(sourceId, state, true);
    }

    private static SprLayout readSprLayout() {
        try {
            Class<?> soundPhysics = Class.forName(
                "com.sonicether.soundphysics.SoundPhysics",
                false,
                HQPrivateEfxClient.class.getClassLoader());

            int[] slots = {
                readPrivateStaticInt(soundPhysics, "auxFXSlot0"),
                readPrivateStaticInt(soundPhysics, "auxFXSlot1"),
                readPrivateStaticInt(soundPhysics, "auxFXSlot2"),
                readPrivateStaticInt(soundPhysics, "auxFXSlot3")
            };
            int maxAuxSends = readPrivateStaticInt(soundPhysics, "maxAuxSends");
            if (maxAuxSends <= 0) return null;

            for (int i = 0; i < 4; i++) {
                int requiredSends = 4 - i;
                if (maxAuxSends >= requiredSends && slots[i] == 0) return null;
            }
            return new SprLayout(slots, maxAuxSends);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (LAYOUT_WARNING_LOGGED.compareAndSet(false, true)) {
                HQSpeakerMod.warn("Could not read Sound Physics auxiliary EFX layout; using native SPR filters: "
                    + failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            }
            return null;
        }
    }

    private static int readPrivateStaticInt(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (!field.canAccess(null) && !field.trySetAccessible()) {
            throw new IllegalAccessException("cannot access SPR field " + name);
        }
        return field.getInt(null);
    }

    private static void deleteFilter(int filter) {
        if (filter == 0) return;
        try {
            EXTEfx.alDeleteFilters(filter);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void drainErrors() {
        try {
            for (int i = 0; i < 8 && AL10.alGetError() != AL10.AL_NO_ERROR; i++) {
                // Drain stale errors so failures are attributed to this operation.
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private record SprLayout(int[] auxSlots, int maxAuxSends) {}
}
