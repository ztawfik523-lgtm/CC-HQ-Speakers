package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.media.FiniteMediaFormat;
import com.tom.hqspeaker.media.MediaAsset;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The physical CC speaker exposed to Lua.
 *
 * Standard speaker calls stay on CC:T's original SpeakerPeripheral. HQ-specific continuous sources have one
 * explicit owner so old/prepared finite state cannot accidentally capture controls belonging to a later source.
 * ComputerCraft may call this peripheral from multiple computer threads, so ownership-changing entry points are
 * synchronized to keep stop -> start -> owner changes in one order.
 */
public final class HQSpeakerCompositePeripheral implements IDynamicPeripheral {
    private static final MethodSupplier<PeripheralMethod> METHOD_SUPPLIER = PeripheralMethodSupplier.create(List.of());
    private static final Set<String> STANDARD = Set.of("playNote", "playSound", "playAudio", "stop");
    private static final Set<String> STANDARD_ALL = Set.of("playNoteAll", "playSoundAll", "playAudioAll");
    private static final Set<String> STANDARD_AT = Set.of("playNoteAt", "playSoundAt", "playAudioAt");
    private static final Set<String> FINITE_CONTROLS = Set.of(
        "audioStatus", "audioPause", "audioResume", "audioSeek", "audioSetVolume", "audioSetLooping", "audioStop"
    );
    private static final Set<String> FINITE_SHARED_CONTROLS = Set.of(
        "audioPause", "audioResume", "audioSeek", "audioSetLooping", "audioStop"
    );
    private static final Set<String> FINITE_AT_SHARED_CONTROLS = Set.of(
        "audioPauseAt", "audioResumeAt", "audioSeekAt", "audioSetLoopingAt"
    );
    private static final Set<String> MODERN_BYTE_FINITE = Set.of(
        "speakMp3", "speakWav", "speakMp3All", "speakWavAll", "speakMp3At", "speakWavAt"
    );
    private static final Set<String> RAW_START = Set.of("speakPCM");
    private static final Set<String> RAW_ALL = Set.of("speakPCMAll");
    private static final Set<String> RAW_AT = Set.of("speakPCMAt");
    private static final Set<String> STREAM_START = Set.of("speakStream", "speakHLS", "speakTS");
    private static final Set<String> STREAM_ALL = Set.of("speakStreamAll");
    private static final Set<String> STREAM_AT = Set.of("speakStreamAt");
    private static final Set<String> STREAM_DIRECT = Set.of(
        "speakHLSAll", "speakTSAll", "speakHLSAt", "speakTSAt"
    );
    private static final Set<String> FINITE_ALL_CONTROLS = Set.of(
        "audioStatusAll", "audioPauseAll", "audioResumeAll", "audioSeekAll",
        "audioSetVolumeAll", "audioSetLoopingAll", "audioStopAll"
    );
    private static final Set<String> FINITE_AT_CONTROLS = Set.of(
        "audioStatusAt", "audioPauseAt", "audioResumeAt", "audioSeekAt",
        "audioSetVolumeAt", "audioSetLoopingAt", "audioStopAt"
    );
    /** Dynamic calls which observe state/capabilities but do not supersede an in-flight stream start. */
    private static final Set<String> READ_ONLY_DYNAMIC = Set.of(
        "audioStatus", "audioStatusAll", "audioStatusAt",
        "getPeripheralType", "getPos", "getSpeakerCount",
        "getStreamArtist", "getStreamFormats", "getStreamGenre", "getStreamMeta", "getStreamMetaSerial",
        "getStreamSong", "getStreamStation", "getStreamTitle", "getStreamUrl", "isStreaming",
        "speakIsPlaying", "speakMaxAudioBytes", "speakMaxSamples",
        "speakQueueSize", "speakSampleRate", "speakSupportedFiles"
    );
    private static final String[] SUPPORTED_FINITE_FILES = { "mp3", "wav" };

    /** Exact inherited single-speaker RAW limits. */
    private static final int HQ_RAW_MAX_SAMPLES = 131_072;
    private static final int HQ_RAW_QUEUE_LIMIT = 16;
    /** One maximum speakPCM call plus 100 ms so the next packet can arrive before the previous chunk runs dry. */
    private static final long HQ_RAW_BUFFER_SAMPLES = HQ_RAW_MAX_SAMPLES + 2L * RawFeedLifetime.SAMPLES_PER_TICK;

    private static final Set<HQSpeakerCompositePeripheral> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Set<HQSpeakerCompositePeripheral>> COMPUTER_SPEAKERS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_LOCK_ORDER = new AtomicLong();

    private enum Owner {
        NONE,
        RAW,
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
    private final long lockOrder = NEXT_LOCK_ORDER.getAndIncrement();

    /**
     * Serializes normal Lua audio command commits without coupling them to the ownership monitor used by
     * server tick/cleanup. Blocking URL validation must never hold this lock: audioPlayPrepared is a CC:T
     * main-thread method, so letting DNS hold commandLock could otherwise stall the Minecraft server indirectly.
     */
    private final Object commandLock = new Object();
    /**
     * Monotonic mutation sequence used to reject a normal single-speaker stream start whose DNS validation finishes
     * after a newer playback/control command has already been issued.
     */
    private final AtomicLong commandRevision = new AtomicLong();

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
        names.addAll(STANDARD_ALL);
        names.addAll(STANDARD_AT);
        names.addAll(MODERN_BYTE_FINITE);
        names.addAll(RAW_START);
        names.addAll(RAW_ALL);
        names.addAll(RAW_AT);
        names.addAll(FINITE_CONTROLS);
        names.addAll(FINITE_ALL_CONTROLS);
        names.addAll(FINITE_AT_CONTROLS);
        names.add("setLooping");
        names.add("speakMaxSamples");
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

        if (rawLifetime.tick(queueHasData)) {
            legacy.speakStop();
            rawCapacityWaiters.clear();
            rawLifetime.clear();
            owner = Owner.NONE;
            return;
        }

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
        COMPUTER_SPEAKERS.computeIfAbsent(computer.getID(), ignored -> ConcurrentHashMap.newKeySet()).add(this);
        IComputerAccess filtered = legacyComputerViews.computeIfAbsent(computer, this::filteredLegacyAccess);
        legacy.attach(filtered);
    }

    @Override
    public void detach(IComputerAccess computer) {
        rawCapacityWaiters.remove(computer);
        finite.detach(computer);
        staging.detach(computer);
        unregisterComputer(computer.getID(), this);
        IComputerAccess filtered = legacyComputerViews.remove(computer);
        if (filtered != null) legacy.detach(filtered);
        vanilla.detach(computer);
    }

    public synchronized void cleanup() {
        ACTIVE.remove(this);
        for (IComputerAccess computer : new ArrayList<>(legacyComputerViews.keySet())) {
            unregisterComputer(computer.getID(), this);
        }
        rawCapacityWaiters.clear();
        owner = Owner.NONE;
        rawLifetime.clear();
        finite.cleanup();
        staging.cleanup();
        legacy.cleanup();
        legacyComputerViews.clear();
    }

    private static void unregisterComputer(int computerId, HQSpeakerCompositePeripheral peripheral) {
        Set<HQSpeakerCompositePeripheral> members = COMPUTER_SPEAKERS.get(computerId);
        if (members == null) return;
        members.remove(peripheral);
        if (members.isEmpty()) COMPUTER_SPEAKERS.remove(computerId, members);
    }

    private static List<HQSpeakerCompositePeripheral> membersFor(IComputerAccess computer) {
        Set<HQSpeakerCompositePeripheral> members = COMPUTER_SPEAKERS.get(computer.getID());
        if (members == null || members.isEmpty()) return List.of();
        ArrayList<HQSpeakerCompositePeripheral> out = new ArrayList<>(members);
        out.sort(Comparator
            .comparingInt((HQSpeakerCompositePeripheral p) -> p.finite.position().getX())
            .thenComparingInt(p -> p.finite.position().getY())
            .thenComparingInt(p -> p.finite.position().getZ()));
        return out;
    }

    private static HQSpeakerCompositePeripheral memberAt(IComputerAccess computer, int index) throws LuaException {
        List<HQSpeakerCompositePeripheral> members = membersFor(computer);
        if (index < 1 || index > members.size()) throw new LuaException("speaker index out of range");
        return members.get(index - 1);
    }

    private static <T, E extends Exception> T withGroupLocks(
            List<HQSpeakerCompositePeripheral> members, OrderedMultiLock.Operation<T, E> operation) throws E {
        ArrayList<OrderedMultiLock.Target> locks = new ArrayList<>(members.size());
        LinkedHashSet<HQSpeakerCompositePeripheral> unique = new LinkedHashSet<>(members);
        for (HQSpeakerCompositePeripheral member : unique) {
            locks.add(new OrderedMultiLock.Target(member.lockOrder, member.commandLock, member));
        }
        return OrderedMultiLock.run(locks, operation);
    }

    private static Map<HQSpeakerCompositePeripheral, Long> reserveCommandRevisions(
            List<HQSpeakerCompositePeripheral> members) {
        LinkedHashMap<HQSpeakerCompositePeripheral, Long> expected = new LinkedHashMap<>();
        for (HQSpeakerCompositePeripheral member : members) {
            expected.put(member, member.commandRevision.incrementAndGet());
        }
        return expected;
    }

    private static boolean revisionsMatch(Map<HQSpeakerCompositePeripheral, Long> expected) {
        for (Map.Entry<HQSpeakerCompositePeripheral, Long> entry : expected.entrySet()) {
            if (!ACTIVE.contains(entry.getKey()) || entry.getKey().commandRevision.get() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static List<HQSpeakerCompositePeripheral> sharedFiniteMembers(HQSpeakerCompositePeripheral anchor) {
        ArrayList<HQSpeakerCompositePeripheral> members = new ArrayList<>();
        if (anchor.owner == Owner.STAGED_FINITE) {
            members.add(anchor);
            for (HQSpeakerCompositePeripheral candidate : ACTIVE) {
                if (candidate == anchor || candidate.owner != Owner.STAGED_FINITE) continue;
                if (anchor.finite.sharesPlaybackWith(candidate.finite)) members.add(candidate);
            }
        }
        if (members.isEmpty()) members.add(anchor);
        members.sort(Comparator.comparingLong(member -> member.lockOrder));
        return List.copyOf(members);
    }

    private static MethodResult supersededControlResult(String name) {
        return switch (name) {
            case "stop", "speakStop", "audioStop", "audioStopAll", "audioStopAt" -> MethodResult.of();
            default -> MethodResult.of(false);
        };
    }

    private static boolean sameMembers(List<HQSpeakerCompositePeripheral> left,
                                       List<HQSpeakerCompositePeripheral> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) return false;
        }
        return true;
    }

    private static boolean finiteGroupStillMatches(HQSpeakerCompositePeripheral anchor, boolean expectedShared,
                                                   List<HQSpeakerCompositePeripheral> expectedMembers) {
        if ((anchor.owner == Owner.STAGED_FINITE) != expectedShared) return false;
        return !expectedShared || sameMembers(expectedMembers, sharedFiniteMembers(anchor));
    }

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

    @LuaFunction
    public final String audioPrepareStaged(IComputerAccess computer, String path,
                                           Optional<Boolean> consume) throws LuaException {
        return staging.prepareAsset(computer, path, consume.orElse(true));
    }

    @LuaFunction
    public final Map<String, Object> audioPreparedInfo(String assetId) throws LuaException {
        return staging.preparedInfo(assetId);
    }

    /**
     * Truthful capability surface for the modern prepared engine.
     *
     * <p>Keep this separate from inherited speakSupportedFiles(), which still describes legacy compatibility paths.</p>
     */
    @LuaFunction
    public final Map<String, Boolean> audioPreparedFormats() {
        Map<String, Boolean> formats = new HashMap<>();
        formats.put("mp3", true);
        formats.put("wav", true);
        return formats;
    }

    @LuaFunction(mainThread = true)
    public final boolean audioPlayPrepared(String assetId, Optional<Double> volume) throws LuaException {
        // This direct CC:T main-thread method must invalidate any older stream still blocked in DNS before it waits
        // for the short command commit lock.
        commandRevision.incrementAndGet();
        synchronized (commandLock) {
            synchronized (this) {
                try (HQFiniteMediaServer.PreparedStart prepared =
                         finite.preparePreparedStart(assetId, volume.orElse(1.0))) {
                    beginReplacingHQ(Owner.STAGED_FINITE);
                    if (!finite.commitPreparedStart(prepared)) {
                        throw new IllegalStateException("admitted prepared replacement could not be committed");
                    }
                    owner = Owner.STAGED_FINITE;
                    return true;
                }
            }
        }
    }

    @LuaFunction(mainThread = true)
    public final boolean audioPlayPreparedAll(IComputerAccess computer, String assetId,
                                              Optional<Double> volume) throws LuaException {
        List<HQSpeakerCompositePeripheral> members = membersFor(computer);
        if (members.isEmpty()) members = List.of(this);
        members = List.copyOf(members);

        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(members);
        List<HQFiniteMediaServer> targets = members.stream().map(member -> member.finite).toList();

        try (HQFiniteMediaServer.PreparedGroupStart prepared =
                 finite.preparePreparedGroupStart(targets, assetId, volume.orElse(1.0))) {
            List<HQSpeakerCompositePeripheral> snapshot = members;
            return withGroupLocks(snapshot, () -> {
                if (!revisionsMatch(expectedRevisions)) return false;

                // Admission and asset retention succeeded for the complete snapshot before any current output
                // is replaced. Keep every target reserved through replacement and group commit.
                for (HQSpeakerCompositePeripheral member : snapshot) {
                    member.beginReplacingHQ(Owner.STAGED_FINITE);
                }

                if (!HQFiniteMediaServer.commitPreparedGroupStart(prepared)) {
                    throw new IllegalStateException("admitted prepared multispeaker replacement could not be committed");
                }
                for (HQSpeakerCompositePeripheral member : snapshot) {
                    member.owner = Owner.STAGED_FINITE;
                }
                return true;
            });
        }
    }

    @LuaFunction
    public final boolean audioSetMuted(boolean muted) {
        commandRevision.incrementAndGet();
        synchronized (commandLock) {
            synchronized (this) {
                return owner == Owner.STAGED_FINITE && finite.setMuted(muted);
            }
        }
    }

    @LuaFunction
    public final boolean audioSetMutedAll(IComputerAccess computer, boolean muted) {
        boolean expectedShared = owner == Owner.STAGED_FINITE;
        List<HQSpeakerCompositePeripheral> targets =
            expectedShared ? sharedFiniteMembers(this) : List.of(this);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)
                    || !finiteGroupStillMatches(this, expectedShared, targets)
                    || !expectedShared) return false;
            return finite.setMutedAll(muted);
        });
    }

    @LuaFunction
    public final boolean audioSetMutedAt(IComputerAccess computer, int index, boolean muted) throws LuaException {
        HQSpeakerCompositePeripheral member = memberAt(computer, index);
        List<HQSpeakerCompositePeripheral> targets = List.of(member);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)) return false;
            return member.owner == Owner.STAGED_FINITE && member.finite.setMuted(muted);
        });
    }

    @LuaFunction
    public final boolean audioReleasePrepared(IComputerAccess computer, String assetId) throws LuaException {
        return staging.releasePrepared(computer, assetId);
    }

    @LuaFunction
    public final long audioMaxStagedBytes() {
        return staging.maxStagedBytes();
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments args) throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        if (MODERN_BYTE_FINITE.contains(name)) {
            return startModernByteFinite(name, computer, context, args);
        }

        // DNS may block. Keep it outside commandLock as well as the ownership monitor, otherwise a direct
        // main-thread audioPlayPrepared call could wait behind DNS and stall the Minecraft server.
        if (STREAM_START.contains(name)) {
            long revision = commandRevision.incrementAndGet();
            return startStreamReplacing(name, args, revision);
        }
        if (STREAM_ALL.contains(name)) return startStreamAllReplacing(name, computer, args);
        if (STREAM_AT.contains(name)) return startStreamAtReplacing(name, computer, args);
        if (STREAM_DIRECT.contains(name)) {
            commandRevision.incrementAndGet();
            return invokeLegacy(name, computer, context, args);
        }

        // Commands which reserve another endpoint or a whole playback snapshot must enter without already holding
        // the caller's command lock, otherwise the stable multi-lock order can be inverted.
        if ("stop".equals(name) || "speakStop".equals(name)) {
            return callStopCoordinated(name, computer, context, args);
        }
        if ("setLooping".equals(name)) return callSetLoopingCoordinated(computer, context, args);
        if (FINITE_SHARED_CONTROLS.contains(name)) {
            return callFiniteSharedControlCoordinated(name, computer, context, args);
        }
        if (STANDARD_ALL.contains(name)) return callStandardAllCoordinated(name, computer, context, args);
        if (STANDARD_AT.contains(name)) return callStandardAtCoordinated(name, computer, context, args);
        if (FINITE_ALL_CONTROLS.contains(name) && !"audioStatusAll".equals(name)) {
            return callFiniteAllControlCoordinated(name, computer, context, args);
        }
        if (FINITE_AT_CONTROLS.contains(name) && !"audioStatusAt".equals(name)) {
            return callFiniteAtControlCoordinated(name, computer, context, args);
        }
        if (RAW_ALL.contains(name)) return startRawAll(computer, name, args);
        if (RAW_AT.contains(name)) return startRawAt(computer, name, args);

        if (!READ_ONLY_DYNAMIC.contains(name)) commandRevision.incrementAndGet();
        synchronized (commandLock) {
            return callMethodOrdered(computer, context, method, args);
        }
    }

    private MethodResult callStopCoordinated(String name, IComputerAccess computer, ILuaContext context,
                                             IArguments args) throws LuaException {
        boolean expectedShared = owner == Owner.STAGED_FINITE;
        List<HQSpeakerCompositePeripheral> targets =
            expectedShared ? sharedFiniteMembers(this) : List.of(this);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)
                    || !finiteGroupStillMatches(this, expectedShared, targets)) {
                return supersededControlResult(name);
            }
            if ("stop".equals(name)) return callStandard(name, context, args);
            stopEverything();
            return MethodResult.of();
        });
    }

    private MethodResult callSetLoopingCoordinated(IComputerAccess computer, ILuaContext context,
                                                    IArguments args) throws LuaException {
        boolean expectedShared = owner == Owner.STAGED_FINITE;
        List<HQSpeakerCompositePeripheral> targets =
            expectedShared ? sharedFiniteMembers(this) : List.of(this);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)
                    || !finiteGroupStillMatches(this, expectedShared, targets)) return MethodResult.of(false);
            if (expectedShared) return MethodResult.of(finite.setLooping(args.getBoolean(0)));
            return invokeLegacy("setLooping", computer, context, args);
        });
    }

    private MethodResult callFiniteSharedControlCoordinated(String name, IComputerAccess computer,
                                                            ILuaContext context, IArguments args) throws LuaException {
        boolean expectedShared = owner == Owner.STAGED_FINITE;
        List<HQSpeakerCompositePeripheral> targets =
            expectedShared ? sharedFiniteMembers(this) : List.of(this);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)
                    || !finiteGroupStillMatches(this, expectedShared, targets)) {
                return supersededControlResult(name);
            }
            return callFiniteControl(name, computer, context, args);
        });
    }

    private MethodResult callStandardAllCoordinated(String name, IComputerAccess computer, ILuaContext context,
                                                    IArguments args) throws LuaException {
        List<HQSpeakerCompositePeripheral> targets = membersFor(computer);
        if (targets.isEmpty()) targets = List.of(this);
        targets = List.copyOf(targets);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        List<HQSpeakerCompositePeripheral> snapshot = targets;
        return withGroupLocks(snapshot, () -> {
            if (!revisionsMatch(expectedRevisions)) return MethodResult.of(false);
            return callStandardAllResolved(name, snapshot, context, args);
        });
    }

    private MethodResult callStandardAtCoordinated(String name, IComputerAccess computer, ILuaContext context,
                                                   IArguments args) throws LuaException {
        HQSpeakerCompositePeripheral member = memberAt(computer, args.getInt(0));
        List<HQSpeakerCompositePeripheral> targets = List.of(member);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)) return MethodResult.of(false);
            return callStandardAtResolved(name, member, context, args);
        });
    }

    private MethodResult callFiniteAllControlCoordinated(String name, IComputerAccess computer, ILuaContext context,
                                                         IArguments args) throws LuaException {
        boolean expectedShared = owner == Owner.STAGED_FINITE;
        List<HQSpeakerCompositePeripheral> targets;
        if (expectedShared) {
            targets = sharedFiniteMembers(this);
        } else {
            targets = membersFor(computer);
            if (targets.isEmpty()) targets = List.of(this);
        }
        targets = List.copyOf(targets);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        List<HQSpeakerCompositePeripheral> snapshot = targets;
        return withGroupLocks(snapshot, () -> {
            if (!revisionsMatch(expectedRevisions)) return supersededControlResult(name);
            if (expectedShared && !finiteGroupStillMatches(this, true, snapshot)) {
                return supersededControlResult(name);
            }
            if (!expectedShared && owner == Owner.STAGED_FINITE) {
                return supersededControlResult(name);
            }
            if (!expectedShared && "audioStopAll".equals(name)) {
                for (HQSpeakerCompositePeripheral member : snapshot) member.stopCurrentHQ();
                return MethodResult.of();
            }
            return callFiniteAllControl(name, computer, context, args);
        });
    }

    private MethodResult callFiniteAtControlCoordinated(String name, IComputerAccess computer, ILuaContext context,
                                                        IArguments args) throws LuaException {
        HQSpeakerCompositePeripheral member = memberAt(computer, args.getInt(0));
        boolean expectedShared =
            member.owner == Owner.STAGED_FINITE && FINITE_AT_SHARED_CONTROLS.contains(name);
        List<HQSpeakerCompositePeripheral> targets =
            expectedShared ? sharedFiniteMembers(member) : List.of(member);
        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        return withGroupLocks(targets, () -> {
            if (!revisionsMatch(expectedRevisions)) return supersededControlResult(name);
            if (FINITE_AT_SHARED_CONTROLS.contains(name)
                    && !finiteGroupStillMatches(member, expectedShared, targets)) {
                return supersededControlResult(name);
            }
            return callFiniteAtControlResolved(name, member, computer, context, args);
        });
    }

    private MethodResult callMethodOrdered(IComputerAccess computer, ILuaContext context, int method, IArguments args)
            throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        if (STANDARD.contains(name)) {
            synchronized (this) { return callStandard(name, context, args); }
        }
        if (FINITE_CONTROLS.contains(name)) {
            synchronized (this) { return callFiniteControl(name, computer, context, args); }
        }
        if (FINITE_ALL_CONTROLS.contains(name)) {
            return callFiniteAllControl(name, computer, context, args);
        }
        if (FINITE_AT_CONTROLS.contains(name)) {
            return callFiniteAtControl(name, computer, context, args);
        }

        if ("speakMaxSamples".equals(name)) return MethodResult.of(HQ_RAW_MAX_SAMPLES);
        if ("speakSupportedFiles".equals(name)) return MethodResult.of((Object) SUPPORTED_FINITE_FILES.clone());

        if ("speakIsPlaying".equals(name)) {
            synchronized (this) { return MethodResult.of(isHQContinuousActive()); }
        }

        if (RAW_START.contains(name)) {
            synchronized (this) { return startRaw(computer, context, name, args); }
        }
        synchronized (this) { return invokeLegacy(name, computer, context, args); }
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

    private MethodResult callStandardAllResolved(String name, List<HQSpeakerCompositePeripheral> members,
                                                 ILuaContext context, IArguments args) throws LuaException {
        boolean accepted = false;
        for (HQSpeakerCompositePeripheral member : members) {
            synchronized (member) {
                accepted = switch (name) {
                    case "playNoteAll" -> member.vanilla.playNote(
                        context, args.getString(0), args.optDouble(1), args.optDouble(2)) || accepted;
                    case "playSoundAll" -> {
                        if (member.isHQContinuousActive()) yield accepted;
                        boolean one = member.vanilla.playSound(
                            context, args.getString(0), args.optDouble(1), args.optDouble(2));
                        if (one) member.clearTerminalOwnership();
                        yield one || accepted;
                    }
                    case "playAudioAll" -> {
                        if (member.isHQContinuousActive()) yield accepted;
                        boolean one = member.vanilla.playAudio(
                            context, args.getTableUnsafe(0), args.optDouble(1));
                        if (one) member.clearTerminalOwnership();
                        yield one || accepted;
                    }
                    default -> throw new LuaException("No such standard all-speaker method " + name);
                };
            }
        }
        return MethodResult.of(accepted);
    }

    private MethodResult callStandardAtResolved(String name, HQSpeakerCompositePeripheral member,
                                                ILuaContext context, IArguments args) throws LuaException {
        synchronized (member) {
            return switch (name) {
                case "playNoteAt" -> MethodResult.of(member.vanilla.playNote(
                    context, args.getString(1), args.optDouble(2), args.optDouble(3)));
                case "playSoundAt" -> {
                    if (member.isHQContinuousActive()) yield MethodResult.of(false);
                    boolean accepted = member.vanilla.playSound(
                        context, args.getString(1), args.optDouble(2), args.optDouble(3));
                    if (accepted) member.clearTerminalOwnership();
                    yield MethodResult.of(accepted);
                }
                case "playAudioAt" -> {
                    if (member.isHQContinuousActive()) yield MethodResult.of(false);
                    boolean accepted = member.vanilla.playAudio(
                        context, args.getTableUnsafe(1), args.optDouble(2));
                    if (accepted) member.clearTerminalOwnership();
                    yield MethodResult.of(accepted);
                }
                default -> throw new LuaException("No such indexed standard speaker method " + name);
            };
        }
    }

    /**
     * Compatibility frontend for the old byte-taking MP3/WAV methods.
     *
     * <p>Byte copy/import/analyze stays on the ComputerCraft thread. Only the short ownership replacement and modern
     * finite commit runs through CC:T's main-thread task bridge.</p>
     */
    private MethodResult startModernByteFinite(String name, IComputerAccess computer, ILuaContext context,
                                               IArguments args) throws LuaException {
        boolean all = name.endsWith("All");
        boolean at = name.endsWith("At");
        FiniteMediaFormat expectedFormat = name.startsWith("speakMp3")
            ? FiniteMediaFormat.MP3 : FiniteMediaFormat.WAV;

        List<HQSpeakerCompositePeripheral> targets;
        if (all) {
            targets = membersFor(computer);
            if (targets.isEmpty()) targets = List.of(this);
        } else if (at) {
            targets = List.of(memberAt(computer, args.getInt(0)));
        } else {
            targets = List.of(this);
        }

        int dataIndex = at ? 1 : 0;
        int volumeIndex = at ? 2 : 1;
        ByteBuffer source = args.getBytes(dataIndex);
        byte[] bytes = new byte[source.remaining()];
        source.duplicate().get(bytes);
        if (bytes.length == 0) throw new LuaException(name + ": data is empty");
        if (bytes.length > legacy.speakMaxAudioBytes()) {
            throw new LuaException(name + ": file too large (max "
                + (legacy.speakMaxAudioBytes() / 1024 / 1024) + " MB)");
        }

        double volume = args.optDouble(volumeIndex, legacy.defaultVolume());
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        volume = Math.max(0.0, Math.min(3.0, volume));

        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);

        MediaAsset asset = staging.importAnalyzedBytes(name, bytes, expectedFormat);
        String assetId = asset.id().toString();
        double appliedVolume = volume;
        List<HQSpeakerCompositePeripheral> snapshot = List.copyOf(targets);

        try {
            return context.executeMainThreadTask(() -> {
                try {
                    if (!revisionsMatch(expectedRevisions)) return new Object[]{ false };

                    if (all) {
                        List<HQFiniteMediaServer> finiteTargets =
                            snapshot.stream().map(member -> member.finite).toList();
                        HQFiniteMediaServer coordinator = snapshot.getFirst().finite;
                        try (HQFiniteMediaServer.PreparedGroupStart prepared =
                                 coordinator.preparePreparedGroupStart(finiteTargets, assetId, appliedVolume)) {
                            return withGroupLocks(snapshot, () -> {
                                if (!revisionsMatch(expectedRevisions)) return new Object[]{ false };
                                for (HQSpeakerCompositePeripheral member : snapshot) {
                                    member.beginReplacingHQ(Owner.STAGED_FINITE);
                                }
                                if (!HQFiniteMediaServer.commitPreparedGroupStart(prepared)) {
                                    throw new LuaException(
                                        "admitted " + name + " multispeaker replacement could not be committed");
                                }
                                for (HQSpeakerCompositePeripheral member : snapshot) {
                                    member.owner = Owner.STAGED_FINITE;
                                }
                                return new Object[]{ true };
                            });
                        }
                    }

                    HQSpeakerCompositePeripheral target = snapshot.getFirst();
                    synchronized (target.commandLock) {
                        synchronized (target) {
                            if (target.commandRevision.get() != expectedRevisions.get(target)) {
                                return new Object[]{ false };
                            }
                            try (HQFiniteMediaServer.PreparedStart prepared =
                                     target.finite.preparePreparedStart(assetId, appliedVolume)) {
                                target.beginReplacingHQ(Owner.STAGED_FINITE);
                                if (!target.finite.commitPreparedStart(prepared)) {
                                    throw new LuaException("admitted " + name + " replacement could not be committed");
                                }
                                target.owner = Owner.STAGED_FINITE;
                                return new Object[]{ true };
                            }
                        }
                    }
                } finally {
                    staging.releaseImportedAsset(asset.id(), name + " temporary import");
                }
            });
        } catch (LuaException | RuntimeException e) {
            staging.releaseImportedAsset(asset.id(), name + " unqueued temporary import");
            throw e;
        }
    }

    private MethodResult startRaw(IComputerAccess computer, ILuaContext context, String name, IArguments args) throws LuaException {
        HQSpeakerPeripheral.PreparedPcm prepared = legacy.preparePcm(args);
        validateRawPrepared(name, prepared);
        if (!canAcceptRaw(prepared)) {
            rawCapacityWaiters.put(computer, prepared.samples());
            return MethodResult.of(false);
        }
        return MethodResult.of(commitRawPrepared(computer, prepared, 0L));
    }

    private MethodResult startRawAll(IComputerAccess computer, String name, IArguments args) throws LuaException {
        HQSpeakerPeripheral.PreparedPcm prepared = legacy.preparePcm(args);
        validateRawPrepared(name, prepared);

        List<HQSpeakerCompositePeripheral> members = membersFor(computer);
        if (members.isEmpty()) members = List.of(this);
        members = List.copyOf(members);

        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(members);
        long startTick = members.size() > 1 ? members.getFirst().legacy.nextGroupStartTick() : 0L;
        List<HQSpeakerCompositePeripheral> snapshot = members;

        return withGroupLocks(snapshot, () -> {
            if (!revisionsMatch(expectedRevisions)) return MethodResult.of(false);

            // Preflight and commit happen while the complete target snapshot is reserved. No other Lua command can
            // consume RAW capacity or replace one member between the all-speaker admission check and its commit.
            for (HQSpeakerCompositePeripheral member : snapshot) {
                if (!member.canAcceptRaw(prepared)) {
                    member.rawCapacityWaiters.put(computer, prepared.samples());
                    return MethodResult.of(false);
                }
            }

            for (HQSpeakerCompositePeripheral member : snapshot) {
                if (!member.commitRawPrepared(computer, prepared, startTick)) {
                    throw new IllegalStateException("preflighted RAW multispeaker commit was unexpectedly rejected");
                }
            }
            return MethodResult.of(true);
        });
    }

    private MethodResult startRawAt(IComputerAccess computer, String name, IArguments args) throws LuaException {
        HQSpeakerCompositePeripheral member = memberAt(computer, args.getInt(0));
        HQSpeakerPeripheral.PreparedPcm prepared = member.legacy.preparePcm(args, 1, 2);
        validateRawPrepared(name, prepared);
        member.commandRevision.incrementAndGet();

        synchronized (member.commandLock) {
            synchronized (member) {
                if (!member.canAcceptRaw(prepared)) {
                    member.rawCapacityWaiters.put(computer, prepared.samples());
                    return MethodResult.of(false);
                }
                return MethodResult.of(member.commitRawPrepared(computer, prepared, 0L));
            }
        }
    }

    private static void validateRawPrepared(String name, HQSpeakerPeripheral.PreparedPcm prepared) throws LuaException {
        int samples = prepared == null ? 0 : prepared.samples();
        if (samples <= 0) throw new LuaException(name + ": table is empty");
        if (samples > HQ_RAW_MAX_SAMPLES) throw new LuaException(name + ": table too large");
    }

    private boolean canAcceptRaw(HQSpeakerPeripheral.PreparedPcm prepared) {
        if (owner != Owner.RAW) return true;
        int samples = prepared.samples();
        return legacy.speakQueueSize() < HQ_RAW_QUEUE_LIMIT
            && rawLifetime.canAccept(samples, HQ_RAW_BUFFER_SAMPLES);
    }

    private boolean commitRawPrepared(IComputerAccess computer, HQSpeakerPeripheral.PreparedPcm prepared,
                                      long startTick) {
        if (owner != Owner.RAW) {
            // Validation/admission happened before this replacement, so old output is only stopped after acceptance.
            beginReplacingHQ(Owner.RAW);
        }

        if (!legacy.enqueuePreparedPcmAtTick(prepared, startTick)) {
            if (owner == Owner.RAW) rawCapacityWaiters.put(computer, prepared.samples());
            return false;
        }

        owner = Owner.RAW;
        rawCapacityWaiters.remove(computer);
        rawLifetime.acceptedSamples(prepared.samples());
        return true;
    }

    private MethodResult startStreamAllReplacing(String name, IComputerAccess computer,
                                                 IArguments args) throws LuaException {
        String url = args.getString(0);
        Optional<Double> volume = args.optDouble(1);
        if (volume.isPresent() && !Double.isFinite(volume.get())) throw new LuaException("volume must be finite");

        List<HQSpeakerCompositePeripheral> targets = membersFor(computer);
        if (targets.isEmpty()) targets = List.of(this);
        targets = List.copyOf(targets);

        Map<HQSpeakerCompositePeripheral, Long> expectedRevisions = reserveCommandRevisions(targets);
        LinkedHashMap<HQSpeakerCompositePeripheral, Long> expectedLifecycles = new LinkedHashMap<>();
        for (HQSpeakerCompositePeripheral target : targets) {
            expectedLifecycles.put(target, target.legacy.lifecycleEpochSnapshot());
        }

        HQSpeakerPeripheral.validateStreamUrl(url, name);
        long sealTick = targets.size() > 1 ? targets.getFirst().legacy.nextGroupStartTick() : 0L;
        UUID groupId = targets.size() > 1 ? UUID.randomUUID() : null;
        List<HQSpeakerCompositePeripheral> snapshot = targets;

        return withGroupLocks(snapshot, () -> {
            if (!revisionsMatch(expectedRevisions)) return MethodResult.of(false);
            for (HQSpeakerCompositePeripheral member : snapshot) {
                if (!member.legacy.lifecycleEpochMatches(expectedLifecycles.get(member))) {
                    return MethodResult.of(false);
                }
            }

            for (HQSpeakerCompositePeripheral member : snapshot) member.beginReplacingHQ(Owner.STREAM);

            boolean complete = true;
            for (HQSpeakerCompositePeripheral member : snapshot) {
                boolean started = member.legacy.startValidatedStreamAtTick(
                    url, volume, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM, "speakStream",
                    sealTick, groupId, snapshot.size(), expectedLifecycles.get(member));
                if (!started) {
                    complete = false;
                    break;
                }
            }

            if (!complete) {
                for (HQSpeakerCompositePeripheral member : snapshot) {
                    member.legacy.speakStop();
                    member.owner = Owner.NONE;
                }
                return MethodResult.of(false);
            }

            for (HQSpeakerCompositePeripheral member : snapshot) member.owner = Owner.STREAM;
            return MethodResult.of(true);
        });
    }

    private MethodResult startStreamAtReplacing(String name, IComputerAccess computer,
                                                IArguments args) throws LuaException {
        HQSpeakerCompositePeripheral target = memberAt(computer, args.getInt(0));
        String url = args.getString(1);
        Optional<Double> volume = args.optDouble(2);
        if (volume.isPresent() && !Double.isFinite(volume.get())) throw new LuaException("volume must be finite");

        long expectedRevision = target.commandRevision.incrementAndGet();
        long expectedLifecycle = target.legacy.lifecycleEpochSnapshot();
        HQSpeakerPeripheral.validateStreamUrl(url, name);

        return withGroupLocks(List.of(target), () -> {
            if (target.commandRevision.get() != expectedRevision
                    || !ACTIVE.contains(target)
                    || !target.legacy.lifecycleEpochMatches(expectedLifecycle)) {
                return MethodResult.of(false);
            }
            target.beginReplacingHQ(Owner.STREAM);
            boolean started = target.legacy.startValidatedStreamAtTick(
                url, volume, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM, "speakStream",
                0L, null, 0, expectedLifecycle);
            if (started) target.owner = Owner.STREAM;
            return MethodResult.of(started);
        });
    }

    private MethodResult startStreamReplacing(String name, IArguments args, long expectedCommandRevision) throws LuaException {
        String url = args.getString(0);
        Optional<Double> volume = args.optDouble(1);
        long expectedLifecycle = legacy.lifecycleEpochSnapshot();
        HQSpeakerPeripheral.validateStreamUrl(url, name);

        HQSpeakerAudioPacket.AudioFormat format = switch (name) {
            case "speakStream" -> HQSpeakerAudioPacket.AudioFormat.MP3_STREAM;
            case "speakHLS" -> HQSpeakerAudioPacket.AudioFormat.HLS_STREAM;
            case "speakTS" -> HQSpeakerAudioPacket.AudioFormat.TS_STREAM;
            default -> throw new LuaException("No such stream method " + name);
        };

        // Validation is complete, so the remaining commit is short and may safely rejoin normal command ordering.
        synchronized (commandLock) {
            if (commandRevision.get() != expectedCommandRevision) return MethodResult.of(false);
            synchronized (this) {
                if (!legacy.lifecycleEpochMatches(expectedLifecycle)) return MethodResult.of(false);
                beginReplacingHQ(Owner.STREAM);
                boolean started = legacy.startValidatedStream(url, volume, format, name, expectedLifecycle);
                if (started) owner = Owner.STREAM;
                return MethodResult.of(started);
            }
        }
    }

    private void beginReplacingHQ(Owner requested) {
        if (requested == Owner.RAW && owner == Owner.RAW) return;
        stopCurrentHQ();
        vanilla.stop();
    }

    private void stopCurrentHQ() {
        switch (owner) {
            case STAGED_FINITE -> finite.stop();
            case RAW, STREAM -> legacy.speakStop();
            case NONE -> { }
        }
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private void stopEverything() {
        vanilla.stop();
        if (owner == Owner.STAGED_FINITE) stopFinitePlaybackAndClearOwners();
        else finite.stop();
        legacy.speakStop();
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private void stopFinitePlaybackAndClearOwners() {
        List<HQSpeakerCompositePeripheral> affected = new ArrayList<>();
        for (HQSpeakerCompositePeripheral candidate : ACTIVE) {
            if (candidate.owner == Owner.STAGED_FINITE && finite.sharesPlaybackWith(candidate.finite)) {
                affected.add(candidate);
            }
        }
        finite.stopPlayback();
        for (HQSpeakerCompositePeripheral candidate : affected) {
            // owner is volatile. Do not nest composite monitors here: two endpoints may stop the same shared
            // playback concurrently. A replacement which won the race has either changed owner or installed an
            // active finite session, so it must not be cleared.
            if (candidate.owner == Owner.STAGED_FINITE && !candidate.finite.isActive()) {
                candidate.owner = Owner.NONE;
            }
        }
    }

    private boolean isHQContinuousActive() {
        return switch (owner) {
            case NONE -> false;
            case RAW -> rawLifetime.active(legacy.speakIsPlaying());
            case STAGED_FINITE -> finite.isActive();
            case STREAM -> legacy.isStreaming();
        };
    }

    private void clearTerminalOwnership() {
        if (!isHQContinuousActive()) {
            if (owner == Owner.STAGED_FINITE) finite.stop();
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
                case STREAM -> invokeLegacy(name, computer, context, args);
                case RAW -> MethodResult.of(rawStatus());
                case NONE -> MethodResult.of(idleStatus());
            };
        }

        if ("audioStop".equals(name)) {
            if (owner == Owner.STAGED_FINITE) stopFinitePlaybackAndClearOwners();
            else stopCurrentHQ();
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

        return MethodResult.of(false);
    }

    private MethodResult callFiniteAllControl(String name, IComputerAccess computer, ILuaContext context,
                                              IArguments args) throws LuaException {
        if (owner != Owner.STAGED_FINITE) return invokeLegacy(name, computer, context, args);

        return switch (name) {
            case "audioStatusAll" -> MethodResult.of(finite.hasStatus() ? finite.status() : idleStatus());
            case "audioPauseAll" -> MethodResult.of(finite.pause());
            case "audioResumeAll" -> MethodResult.of(finite.resume());
            case "audioSeekAll" -> MethodResult.of(finite.seek(args.getDouble(0)));
            case "audioSetLoopingAll" -> MethodResult.of(finite.setLooping(args.getBoolean(0)));
            case "audioSetVolumeAll" -> MethodResult.of(finite.setVolumeAll(args.getDouble(0)));
            case "audioStopAll" -> {
                stopFinitePlaybackAndClearOwners();
                yield MethodResult.of();
            }
            default -> invokeLegacy(name, computer, context, args);
        };
    }

    private MethodResult callFiniteAtControl(String name, IComputerAccess computer, ILuaContext context,
                                             IArguments args) throws LuaException {
        return callFiniteAtControlResolved(
            name, memberAt(computer, args.getInt(0)), computer, context, args);
    }

    private MethodResult callFiniteAtControlResolved(String name, HQSpeakerCompositePeripheral member,
                                                     IComputerAccess computer, ILuaContext context,
                                                     IArguments args) throws LuaException {
        synchronized (member) {
            if ("audioStopAt".equals(name)) {
                member.stopCurrentHQ();
                return MethodResult.of();
            }
            if (member.owner != Owner.STAGED_FINITE) return invokeLegacy(name, computer, context, args);

            return switch (name) {
                case "audioStatusAt" -> MethodResult.of(member.finite.hasStatus() ? member.finite.status() : idleStatus());
                case "audioPauseAt" -> MethodResult.of(member.finite.pause());
                case "audioResumeAt" -> MethodResult.of(member.finite.resume());
                case "audioSeekAt" -> MethodResult.of(member.finite.seek(args.getDouble(1)));
                case "audioSetVolumeAt" -> MethodResult.of(member.finite.setVolume(args.getDouble(1)));
                case "audioSetLoopingAt" -> MethodResult.of(member.finite.setLooping(args.getBoolean(1)));
                default -> invokeLegacy(name, computer, context, args);
            };
        }
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

    private MethodResult invokeLegacy(String name, IComputerAccess computer, ILuaContext context, IArguments args) throws LuaException {
        PeripheralMethod legacyMethod = legacyMethods.get(name);
        if (legacyMethod == null) throw new LuaException("No such method " + name);
        return legacyMethod.apply(legacy, context, computer, args);
    }
}
