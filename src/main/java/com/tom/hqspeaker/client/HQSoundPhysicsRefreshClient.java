package com.tom.hqspeaker.client;

import com.mojang.blaze3d.audio.Channel;
import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.mixin.client.ChannelAccessor;
import com.tom.hqspeaker.mixin.client.SoundEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import org.lwjgl.openal.AL10;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HQ-only live Sound Physics refresh scheduler.
 *
 * <p>Sound Physics keeps ownership of the actual acoustic calculation and OpenAL filters. This class only
 * re-runs the normal processSound path when an HQ source materially moves, settles after movement, or reaches
 * a fixed safety-staleness deadline. Work is globally staggered to at most one full refresh per client tick.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HQSoundPhysicsRefreshClient {
    private static final String SPR_MOD_ID = "sound_physics_remastered";
    private static final String HQ_SOUND_ID = "hqspeaker:hq_audio_source";
    private static final ResourceLocation HQ_SOUND =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_audio_source");

    private static final ConcurrentHashMap<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Binding> BY_OPENAL_SOURCE = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_LIFETIME = new AtomicLong();
    private static final AtomicLong NEXT_TASK_TOKEN = new AtomicLong();
    private static final AtomicLong ACTIVE_TASK_TOKEN = new AtomicLong();
    private static final AtomicBoolean INVOCATION_WARNING_LOGGED = new AtomicBoolean();

    private static volatile Method processSoundMethod;
    private static volatile boolean processSoundLookupAttempted;

    private HQSoundPhysicsRefreshClient() {}

    public static void attach(
        SoundEngine engine,
        SoundInstance sound,
        Channel channel,
        HQDiagnosticSource diagnostic
    ) {
        if (!ModList.get().isLoaded(SPR_MOD_ID)
            || engine == null || sound == null || channel == null || diagnostic == null
            || diagnostic.hqspeaker$diagnosticIdentity() == null) {
            return;
        }

        UUID source = diagnostic.hqspeaker$diagnosticIdentity().source();
        if (source == null) return;

        int openAlSource = ((ChannelAccessor) (Object) channel).hqspeaker$getSource();
        SoundEngineExecutor executor = ((SoundEngineAccessor) (Object) engine).hqspeaker$getExecutor();
        if (openAlSource <= 0 || executor == null) return;

        Vec3 listener = currentListener();
        if (listener == null) return;

        long now = System.nanoTime();
        Binding binding = new Binding(
            source,
            NEXT_LIFETIME.incrementAndGet(),
            sound,
            openAlSource,
            executor,
            now,
            listener,
            sound.getX(), sound.getY(), sound.getZ());

        Binding oldIdentity = BINDINGS.put(source, binding);
        if (oldIdentity != null) {
            BY_OPENAL_SOURCE.remove(oldIdentity.openAlSource(), oldIdentity);
        }

        Binding oldSource = BY_OPENAL_SOURCE.put(openAlSource, binding);
        if (oldSource != null && oldSource != binding) {
            BINDINGS.remove(oldSource.source(), oldSource);
        }

        // Channel-start processing may happen before this event. Force one immediate HQ-owned refresh
        // so the approved progressive/smoothing path is installed promptly without enabling SPR's
        // global moving-sounds option.
        synchronized (binding) {
            binding.policy.markUrgent();
        }
        HQSoundPhysicsAcousticsClient.attach(source, openAlSource);
    }

    public static void detach(UUID source) {
        if (source == null) return;
        Binding removed = BINDINGS.remove(source);
        if (removed != null) BY_OPENAL_SOURCE.remove(removed.openAlSource(), removed);
        HQSoundPhysicsAcousticsClient.detach(source);
    }

    public static void setPaused(UUID source, boolean paused) {
        Binding binding = source == null ? null : BINDINGS.get(source);
        if (binding == null) return;
        synchronized (binding) {
            boolean wasPaused = binding.paused;
            binding.paused = paused;
            if (wasPaused && !paused) binding.policy.markUrgent();
        }
    }

    public static void markUrgent(UUID source) {
        Binding binding = source == null ? null : BINDINGS.get(source);
        if (binding == null) return;
        synchronized (binding) {
            binding.policy.markUrgent();
        }
    }

    /**
     * Observe any real SPR processSound call for an already-bound HQ channel.
     *
     * <p>This also makes the scheduler automatically back off when SPR's own global moving-sound option is enabled:
     * those native refreshes continuously reset our movement/safety baseline instead of being duplicated.</p>
     */
    public static void soundPhysicsProcessed(
        int openAlSource,
        double sourceX, double sourceY, double sourceZ,
        String sound
    ) {
        if (openAlSource <= 0 || !HQ_SOUND_ID.equals(sound)) return;
        Binding binding = BY_OPENAL_SOURCE.get(openAlSource);
        if (binding == null || BINDINGS.get(binding.source()) != binding) return;

        Vec3 listener = currentListener();
        if (listener == null) return;

        synchronized (binding) {
            binding.policy.recordExternalRefresh(
                System.nanoTime(),
                listener.x, listener.y, listener.z,
                sourceX, sourceY, sourceZ);
        }
    }

    public static void soundEngineReloaded() {
        BINDINGS.clear();
        BY_OPENAL_SOURCE.clear();
        ACTIVE_TASK_TOKEN.set(0L);
        HQSoundPhysicsAcousticsClient.clear();
    }

    public static void tick() {
        if (BINDINGS.isEmpty() || !ModList.get().isLoaded(SPR_MOD_ID)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Vec3 listener = currentListener();
        if (listener == null) return;

        long now = System.nanoTime();
        Candidate best = null;

        for (Map.Entry<UUID, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            if (binding == null || BINDINGS.get(entry.getKey()) != binding) continue;

            if (!minecraft.getSoundManager().isActive(binding.sound())) {
                if (now - binding.attachedNanos() >= HQSoundPhysicsRefreshPolicy.SAFETY_REFRESH_NANOS) {
                    removeBinding(binding);
                }
                continue;
            }

            HQSoundPhysicsRefreshPolicy.Reason reason;
            long lastRefresh;
            synchronized (binding) {
                if (binding.paused) continue;
                reason = binding.policy.due(
                    now,
                    listener.x, listener.y, listener.z,
                    binding.sound().getX(), binding.sound().getY(), binding.sound().getZ());
                lastRefresh = binding.policy.lastRefreshNanos();
            }
            if (reason == HQSoundPhysicsRefreshPolicy.Reason.NONE) continue;

            Candidate candidate = new Candidate(binding, reason, lastRefresh);
            if (best == null || candidate.before(best)) best = candidate;
        }

        if (best == null) return;

        long token = NEXT_TASK_TOKEN.incrementAndGet();
        if (token == 0L) token = NEXT_TASK_TOKEN.incrementAndGet();
        if (!ACTIVE_TASK_TOKEN.compareAndSet(0L, token)) return;

        Candidate selected = best;
        long taskToken = token;
        selected.binding().executor().execute(() -> runRefresh(selected, taskToken));
    }

    private static void runRefresh(Candidate selected, long taskToken) {
        try {
            Binding binding = selected.binding();
            if (BINDINGS.get(binding.source()) != binding
                || BY_OPENAL_SOURCE.get(binding.openAlSource()) != binding) {
                return;
            }

            synchronized (binding) {
                if (binding.paused) return;
            }

            int source = binding.openAlSource();
            if (!AL10.alIsSource(source)) {
                removeBinding(binding);
                return;
            }

            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) return;

            SoundInstance sound = binding.sound();
            AcousticPosition physical = physicalPosition(sound);
            double x = physical.x();
            double y = physical.y();
            double z = physical.z();

            if (!invokeSoundPhysics(source, x, y, z)) return;

            // The optional processSound mixin normally records the exact listener position used by SPR.
            // Keep this explicit record as a safe fallback if that observation hook ever stops matching.
            Vec3 listener = currentListener();
            if (listener == null) return;
            synchronized (binding) {
                if (BINDINGS.get(binding.source()) == binding) {
                    binding.policy.recordRefresh(
                        System.nanoTime(),
                        listener.x, listener.y, listener.z,
                        x, y, z,
                        selected.reason());
                }
            }
        } finally {
            ACTIVE_TASK_TOKEN.compareAndSet(taskToken, 0L);
        }
    }

    private static boolean invokeSoundPhysics(int source, double x, double y, double z) {
        Method method = resolveProcessSound();
        if (method == null) return false;

        try {
            method.invoke(null, source, x, y, z, SoundSource.BLOCKS, HQ_SOUND, false);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            if (INVOCATION_WARNING_LOGGED.compareAndSet(false, true)) {
                Throwable cause = failure instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : failure;
                HQSpeakerMod.warn("HQ Sound Physics live refresh failed; leaving native playback unchanged: "
                    + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            }
            return false;
        }
    }

    private static Method resolveProcessSound() {
        if (processSoundLookupAttempted) return processSoundMethod;
        synchronized (HQSoundPhysicsRefreshClient.class) {
            if (processSoundLookupAttempted) return processSoundMethod;
            processSoundLookupAttempted = true;
            try {
                Class<?> soundPhysics = Class.forName(
                    "com.sonicether.soundphysics.SoundPhysics",
                    false,
                    HQSoundPhysicsRefreshClient.class.getClassLoader());
                processSoundMethod = soundPhysics.getMethod(
                    "processSound",
                    int.class,
                    double.class, double.class, double.class,
                    SoundSource.class,
                    ResourceLocation.class,
                    boolean.class);
            } catch (ReflectiveOperationException | LinkageError failure) {
                processSoundMethod = null;
                if (INVOCATION_WARNING_LOGGED.compareAndSet(false, true)) {
                    HQSpeakerMod.warn("Sound Physics is present but its refresh entry point was not found; "
                        + "HQ playback will keep native one-shot SPR processing");
                }
            }
            return processSoundMethod;
        }
    }

    static AcousticSnapshot acousticSnapshot(int openAlSource) {
        Binding binding = BY_OPENAL_SOURCE.get(openAlSource);
        if (binding == null || BINDINGS.get(binding.source()) != binding) return null;

        Vec3 listener = currentListener();
        if (listener == null) return null;

        SoundInstance sound = binding.sound();
        AcousticPosition physical = physicalPosition(sound);
        return new AcousticSnapshot(
            physical.x(), physical.y(), physical.z(),
            listener.x, listener.y, listener.z);
    }

    static void applyAcousticPosition(int openAlSource, double x, double y, double z) {
        Binding binding = BY_OPENAL_SOURCE.get(openAlSource);
        if (binding == null || BINDINGS.get(binding.source()) != binding) return;
        if (binding.sound() instanceof HQAcousticPositionSource acoustic) {
            acoustic.hqspeaker$setAcousticPosition(x, y, z);
        }
    }

    static void clearAcousticPosition(int openAlSource) {
        Binding binding = BY_OPENAL_SOURCE.get(openAlSource);
        if (binding == null || BINDINGS.get(binding.source()) != binding) return;
        if (binding.sound() instanceof HQAcousticPositionSource acoustic) {
            acoustic.hqspeaker$clearAcousticPosition();
        }
    }

    private static AcousticPosition physicalPosition(SoundInstance sound) {
        if (sound instanceof HQAcousticPositionSource acoustic) {
            return new AcousticPosition(
                acoustic.hqspeaker$physicalX(),
                acoustic.hqspeaker$physicalY(),
                acoustic.hqspeaker$physicalZ());
        }
        return new AcousticPosition(sound.getX(), sound.getY(), sound.getZ());
    }

    private record AcousticPosition(double x, double y, double z) {}

    static record AcousticSnapshot(
        double sourceX, double sourceY, double sourceZ,
        double listenerX, double listenerY, double listenerZ
    ) {}

    private static Vec3 currentListener() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null || minecraft.gameRenderer.getMainCamera() == null) return null;
        return minecraft.gameRenderer.getMainCamera().getPosition();
    }

    private static void removeBinding(Binding binding) {
        if (binding == null) return;
        if (BINDINGS.remove(binding.source(), binding)) {
            HQSoundPhysicsAcousticsClient.detach(binding.source());
        }
        BY_OPENAL_SOURCE.remove(binding.openAlSource(), binding);
    }

    private static int priority(HQSoundPhysicsRefreshPolicy.Reason reason) {
        return switch (reason) {
            case HARD_STALE -> 0;
            case URGENT -> 1;
            case MOVEMENT -> 2;
            case SETTLE -> 3;
            case NONE -> 4;
        };
    }

    private static final class Binding {
        private final UUID source;
        private final long lifetime;
        private final SoundInstance sound;
        private final int openAlSource;
        private final SoundEngineExecutor executor;
        private final long attachedNanos;
        private final HQSoundPhysicsRefreshPolicy policy;
        private boolean paused;

        Binding(
            UUID source,
            long lifetime,
            SoundInstance sound,
            int openAlSource,
            SoundEngineExecutor executor,
            long attachedNanos,
            Vec3 listener,
            double sourceX, double sourceY, double sourceZ
        ) {
            this.source = source;
            this.lifetime = lifetime;
            this.sound = sound;
            this.openAlSource = openAlSource;
            this.executor = executor;
            this.attachedNanos = attachedNanos;
            this.policy = new HQSoundPhysicsRefreshPolicy(
                attachedNanos,
                listener.x, listener.y, listener.z,
                sourceX, sourceY, sourceZ);
        }

        UUID source() { return source; }
        long lifetime() { return lifetime; }
        SoundInstance sound() { return sound; }
        int openAlSource() { return openAlSource; }
        SoundEngineExecutor executor() { return executor; }
        long attachedNanos() { return attachedNanos; }
    }

    private record Candidate(
        Binding binding,
        HQSoundPhysicsRefreshPolicy.Reason reason,
        long lastRefreshNanos
    ) {
        boolean before(Candidate other) {
            int ours = priority(reason);
            int theirs = priority(other.reason);
            if (ours != theirs) return ours < theirs;
            if (lastRefreshNanos != other.lastRefreshNanos) return lastRefreshNanos < other.lastRefreshNanos;
            return binding.lifetime() < other.binding.lifetime();
        }
    }
}
