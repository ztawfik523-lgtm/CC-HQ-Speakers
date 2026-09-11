package com.tom.hqspeaker.peripheral;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.core.methods.MethodSupplier;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The physical CC speaker exposed to Lua.
 *
 * Standard speaker calls stay on CC:T's original SpeakerPeripheral. HQ-specific continuous sources have one
 * explicit owner so old/staged finite state cannot accidentally capture controls belonging to a later source.
 * ComputerCraft may call this peripheral from multiple computer threads, so ownership-changing entry points are
 * synchronized to keep stop -> start -> owner changes in one order.
 */
public final class HQSpeakerCompositePeripheral implements IDynamicPeripheral {
    private static final MethodSupplier<PeripheralMethod> METHOD_SUPPLIER = PeripheralMethodSupplier.create(List.of());
    private static final Set<String> STANDARD = Set.of("playNote", "playSound", "playAudio", "stop");
    private static final Set<String> FINITE_CONTROLS = Set.of(
        "audioStatus", "audioPause", "audioResume", "audioSeek", "audioSetVolume", "audioSetLooping", "audioStop"
    );
    private static final Set<String> FINITE_START = Set.of(
        "speakMp3", "speakOgg", "speakAudio", "speakFile", "speakPacked", "speakWav"
    );
    private static final Set<String> RAW_START = Set.of("speakPCM");
    private static final Set<String> STREAM_START = Set.of("speakStream", "speakHLS", "speakTS");
    private static final String[] SUPPORTED_FINITE_FILES = { "wav", "ogg", "mp3", "aiff", "aif", "au", "snd" };

    /** Exact inherited single-speaker RAW limits. */
    private static final int HQ_RAW_MAX_SAMPLES = 131_072;
    private static final int HQ_RAW_QUEUE_LIMIT = 16;
    /** One maximum speakPCM call plus 100 ms so the next packet can arrive before the previous chunk runs dry. */
    private static final long HQ_RAW_BUFFER_SAMPLES = HQ_RAW_MAX_SAMPLES + 2L * RawFeedLifetime.SAMPLES_PER_TICK;

    private static final Set<HQSpeakerCompositePeripheral> ACTIVE = ConcurrentHashMap.newKeySet();

    private enum Owner {
        NONE,
        RAW,
        LEGACY_FINITE,
        STAGED_FINITE,
        STREAM
    }

    private final HQSpeakerPeripheral legacy;
    private final SpeakerPeripheral vanilla;
    private final HQFiniteMediaServer finite;
    private final HQMediaStaging staging;
    private final Map<String, PeripheralMethod> legacyMethods;
    private final String[] dynamicNames;
    private final Map<IComputerAccess, IComputerAccess> legacyComputerViews = new ConcurrentHashMap<>();
    /** Requested sample count for each computer currently waiting for RAW capacity. */
    private final Map<IComputerAccess, Integer> rawCapacityWaiters = new ConcurrentHashMap<>();
    private final RawFeedLifetime rawLifetime = new RawFeedLifetime();

    private volatile Owner owner = Owner.NONE;

    public HQSpeakerCompositePeripheral(HQSpeakerPeripheral legacy, SpeakerPeripheral vanilla,
                                        HQFiniteMediaServer finite, HQMediaStaging staging) {
        this.legacy = legacy;
        this.vanilla = vanilla;
        this.finite = finite;
        this.staging = staging;
        this.legacyMethods = METHOD_SUPPLIER.getSelfMethods(legacy);
        LinkedHashSet<String> names = new LinkedHashSet<>(legacyMethods.keySet());
        names.addAll(STANDARD);
        dynamicNames = names.toArray(String[]::new);
        ACTIVE.add(this);
    }

    @Override public String getType() { return "speaker"; }
    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }
    @Override public String[] getMethodNames() { return dynamicNames.clone(); }

    boolean usesVanilla(SpeakerPeripheral candidate) { return vanilla == candidate; }

    /** Tick only ownership/lifecycle state. Audio dispatch remains in the existing server implementations. */
    public static void tickAll() {
        for (HQSpeakerCompositePeripheral peripheral : ACTIVE) peripheral.tickOwnership();
    }

    private synchronized void tickOwnership() {
        if (owner != Owner.RAW) return;

        boolean queueHasData = legacy.speakIsPlaying();

        // Count down accepted RAW audio at the real 48 kHz playback rate. This prevents the server from feeding the
        // client one multi-second PCM chunk every Minecraft tick just because packet slots are free.
        if (rawLifetime.tick(queueHasData)) {
            legacy.speakStop();
            rawCapacityWaiters.clear();
            rawLifetime.clear();
            owner = Owner.NONE;
            return;
        }

        // hqspeaker_audio_empty means the rejected speakPCM call can now be retried: both the server packet queue and
        // the duration-based RAW buffer have room for that computer's requested sample count.
        if (!rawCapacityWaiters.isEmpty() && legacy.speakQueueSize() < HQ_RAW_QUEUE_LIMIT) {
            for (Map.Entry<IComputerAccess, Integer> entry : rawCapacityWaiters.entrySet()) {
                IComputerAccess computer = entry.getKey();
                int requestedSamples = entry.getValue();
                if (requestedSamples > 0
                        && rawLifetime.canAccept(requestedSamples, HQ_RAW_BUFFER_SAMPLES)
                        && rawCapacityWaiters.remove(computer, requestedSamples)) {
                    computer.queueEvent("hqspeaker_audio_empty", computer.getAttachmentName());
                }
            }
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        vanilla.attach(computer);
        staging.attach(computer);
        finite.attach(computer);
        IComputerAccess filtered = legacyComputerViews.computeIfAbsent(computer, this::filteredLegacyAccess);
        legacy.attach(filtered);
    }

    @Override
    public void detach(IComputerAccess computer) {
        rawCapacityWaiters.remove(computer);
        finite.detach(computer);
        staging.detach(computer);
        IComputerAccess filtered = legacyComputerViews.remove(computer);
        if (filtered != null) legacy.detach(filtered);
        vanilla.detach(computer);
    }

    public synchronized void cleanup() {
        ACTIVE.remove(this);
        rawCapacityWaiters.clear();
        owner = Owner.NONE;
        rawLifetime.clear();
        finite.cleanup();
        staging.cleanup();
        legacy.cleanup();
        legacyComputerViews.clear();
    }

    /**
     * Legacy HQ used speaker_audio_empty as a generic queue heartbeat. That breaks CC:T's documented playAudio
     * contract, so those synthetic events are swallowed. M1A emits hqspeaker_audio_empty itself from actual HQ RAW
     * capacity instead.
     */
    private IComputerAccess filteredLegacyAccess(IComputerAccess delegate) {
        return (IComputerAccess) Proxy.newProxyInstance(
            IComputerAccess.class.getClassLoader(), new Class<?>[]{ IComputerAccess.class },
            (proxy, method, args) -> {
                if ("queueEvent".equals(method.getName()) && args != null && args.length > 0
                        && "speaker_audio_empty".equals(args[0])) return null;
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    @LuaFunction
    public final String audioMountPath(IComputerAccess computer) throws LuaException {
        return staging.mountPath(computer);
    }

    /** Prepare a staged CC file as a reusable server media asset without starting playback. */
    @LuaFunction
    public final String audioPrepareStaged(IComputerAccess computer, String path,
                                           Optional<Boolean> consume) throws LuaException {
        return staging.prepareAsset(computer, path, consume.orElse(true));
    }

    /** Server-derived format/duration facts for a prepared asset. */
    @LuaFunction
    public final Map<String, Object> audioPreparedInfo(String assetId) throws LuaException {
        return staging.preparedInfo(assetId);
    }

    /** Start a prepared asset. The playback takes its own reference before this method returns true. */
    @LuaFunction(mainThread = true)
    public final synchronized boolean audioPlayPrepared(String assetId, Optional<Double> volume) throws LuaException {
        beginReplacingHQ(Owner.STAGED_FINITE);
        boolean started = finite.playPrepared(assetId, volume.orElse(1.0));
        if (started) owner = Owner.STAGED_FINITE;
        return started;
    }

    /** Release the prepared-owner reference held by the calling computer. */
    @LuaFunction
    public final boolean audioReleasePrepared(IComputerAccess computer, String assetId) throws LuaException {
        return staging.releasePrepared(computer, assetId);
    }

    /** Historical direct-staged entrypoint retained while the prototype transport still exists. */
    @LuaFunction(mainThread = true)
    public final synchronized boolean audioPlayStaged(IComputerAccess computer, String path,
                                                      Optional<Double> volume, Optional<Boolean> consume) throws LuaException {
        beginReplacingHQ(Owner.STAGED_FINITE);
        boolean started = finite.playStaged(computer, path, volume.orElse(1.0), consume.orElse(true));
        if (started) owner = Owner.STAGED_FINITE;
        return started;
    }

    @LuaFunction
    public final long audioMaxStagedBytes() {
        return staging.maxStagedBytes();
    }

    @Override
    public synchronized MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments args) throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        if (STANDARD.contains(name)) return callStandard(name, context, args);
        if (FINITE_CONTROLS.contains(name)) return callFiniteControl(name, computer, context, args);

        if ("speakMaxSamples".equals(name)) return MethodResult.of(HQ_RAW_MAX_SAMPLES);
        if ("speakSupportedFiles".equals(name)) return MethodResult.of((Object) SUPPORTED_FINITE_FILES.clone());

        if ("speakStop".equals(name)) {
            stopEverything();
            return MethodResult.of();
        }
        if ("speakIsPlaying".equals(name)) return MethodResult.of(isHQContinuousActive());

        if ("setLooping".equals(name) && owner == Owner.STAGED_FINITE) {
            return MethodResult.of(finite.setLooping(args.getBoolean(0)));
        }

        if (RAW_START.contains(name)) return startRaw(computer, context, name, args);
        if (FINITE_START.contains(name)) return startLegacyReplacing(Owner.LEGACY_FINITE, computer, context, name, args);
        if (STREAM_START.contains(name)) return startLegacyReplacing(Owner.STREAM, computer, context, name, args);

        return invokeLegacy(name, computer, context, args);
    }

    private MethodResult callStandard(String name, ILuaContext context, IArguments args) throws LuaException {
        return switch (name) {
            case "playNote" -> MethodResult.of(vanilla.playNote(context, args.getString(0), args.optDouble(1), args.optDouble(2)));
            case "playSound" -> {
                if (isHQContinuousActive()) yield MethodResult.of(false);
                boolean accepted = vanilla.playSound(context, args.getString(0), args.optDouble(1), args.optDouble(2));
                if (accepted) clearTerminalOwnership();
                yield MethodResult.of(accepted);
            }
            case "playAudio" -> {
                if (isHQContinuousActive()) yield MethodResult.of(false);
                boolean accepted = vanilla.playAudio(context, args.getTableUnsafe(0), args.optDouble(1));
                if (accepted) clearTerminalOwnership();
                yield MethodResult.of(accepted);
            }
            case "stop" -> {
                stopEverything();
                yield MethodResult.of();
            }
            default -> throw new LuaException("No such method " + name);
        };
    }

    private MethodResult startRaw(IComputerAccess computer, ILuaContext context, String name, IArguments args) throws LuaException {
        if (owner != Owner.RAW) beginReplacingHQ(Owner.RAW);

        int samples = contiguousRawSamples(args);
        boolean validSizedChunk = samples > 0 && samples <= HQ_RAW_MAX_SAMPLES;
        if (validSizedChunk && (legacy.speakQueueSize() >= HQ_RAW_QUEUE_LIMIT
                || !rawLifetime.canAccept(samples, HQ_RAW_BUFFER_SAMPLES))) {
            rawCapacityWaiters.put(computer, samples);
            return MethodResult.of(false);
        }

        MethodResult result = invokeLegacy(name, computer, context, args);
        if (immediateTrue(result)) {
            owner = Owner.RAW;
            rawCapacityWaiters.remove(computer);
            rawLifetime.acceptedSamples(samples);
        } else if (validSizedChunk) {
            rawCapacityWaiters.put(computer, samples);
        }
        return result;
    }

    private MethodResult startLegacyReplacing(Owner requested, IComputerAccess computer, ILuaContext context,
                                              String name, IArguments args) throws LuaException {
        beginReplacingHQ(requested);
        MethodResult result = invokeLegacy(name, computer, context, args);
        if (immediateTrue(result)) owner = requested;
        return result;
    }

    private void beginReplacingHQ(Owner requested) {
        if (requested == Owner.RAW && owner == Owner.RAW) return;
        stopCurrentHQ();
        // CC:T stop clears playSound/playAudio but deliberately leaves pending notes alone, so notes retain native
        // independence while an explicit HQ continuous-source start takes ownership of the main output.
        vanilla.stop();
    }

    private void stopCurrentHQ() {
        switch (owner) {
            case STAGED_FINITE -> finite.stop();
            case RAW, LEGACY_FINITE, STREAM -> legacy.speakStop();
            case NONE -> { }
        }
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private void stopEverything() {
        vanilla.stop();
        finite.stop();
        legacy.speakStop();
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private boolean isHQContinuousActive() {
        return switch (owner) {
            case NONE -> false;
            case RAW -> rawLifetime.active(legacy.speakIsPlaying());
            case LEGACY_FINITE -> legacy.speakIsPlaying();
            case STAGED_FINITE -> finite.isActive();
            case STREAM -> legacy.isStreaming();
        };
    }

    private void clearTerminalOwnership() {
        if (!isHQContinuousActive()) {
            owner = Owner.NONE;
            rawCapacityWaiters.clear();
            rawLifetime.clear();
        }
    }

    private MethodResult callFiniteControl(String name, IComputerAccess computer, ILuaContext context,
                                           IArguments args) throws LuaException {
        if ("audioStatus".equals(name)) {
            return switch (owner) {
                case STAGED_FINITE -> MethodResult.of(finite.hasStatus() ? finite.status() : idleStatus());
                case LEGACY_FINITE, STREAM -> invokeLegacy(name, computer, context, args);
                case RAW -> MethodResult.of(rawStatus());
                case NONE -> MethodResult.of(idleStatus());
            };
        }

        // Stop is a source capability, not a finite-only capability. It truthfully ends whichever HQ continuous
        // source currently owns the speaker. Standard stop() additionally stops CC:T's native sound/audio state.
        if ("audioStop".equals(name)) {
            stopCurrentHQ();
            return MethodResult.of();
        }

        if (owner == Owner.STAGED_FINITE) {
            return switch (name) {
                case "audioPause" -> MethodResult.of(finite.pause());
                case "audioResume" -> MethodResult.of(finite.resume());
                case "audioSeek" -> MethodResult.of(finite.seek(args.getDouble(0)));
                case "audioSetVolume" -> MethodResult.of(finite.setVolume(args.getDouble(0)));
                case "audioSetLooping" -> MethodResult.of(finite.setLooping(args.getBoolean(0)));
                default -> MethodResult.of(false);
            };
        }

        if (owner == Owner.LEGACY_FINITE) return invokeLegacy(name, computer, context, args);

        // RAW and live streams have no finite duration/seek/loop contract. Live pause/reconnect is a later milestone.
        return MethodResult.of(false);
    }

    private Map<String, Object> rawStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("state", isHQContinuousActive() ? "playing" : "idle");
        status.put("kind", "raw");
        status.put("observed", false);
        status.put("canPause", false);
        status.put("canSeek", false);
        status.put("canLoop", false);
        return status;
    }

    private static Map<String, Object> idleStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("state", "idle");
        status.put("kind", "none");
        status.put("observed", false);
        status.put("canPause", false);
        status.put("canSeek", false);
        status.put("canLoop", false);
        return status;
    }

    private static int contiguousRawSamples(IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0);
        int length = 0;
        while (length <= HQ_RAW_MAX_SAMPLES
                && (table.containsKey((long) (length + 1)) || table.containsKey((double) (length + 1)))) {
            length++;
        }
        return length;
    }

    private static boolean immediateTrue(MethodResult result) {
        Object[] values = result.getResult();
        return values != null && values.length > 0 && Boolean.TRUE.equals(values[0]);
    }

    private MethodResult invokeLegacy(String name, IComputerAccess computer, ILuaContext context, IArguments args) throws LuaException {
        PeripheralMethod legacyMethod = legacyMethods.get(name);
        if (legacyMethod == null) throw new LuaException("No such method " + name);
        return legacyMethod.apply(legacy, context, computer, args);
    }
}
