package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.config.HQAudioTuningProfile;
import com.tom.hqspeaker.config.HQSpeakerServerConfig;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.network.HQSpeakerStopPacket;
import com.tom.hqspeaker.network.IcyMetaPacket;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


public class HQSpeakerPeripheral implements IPeripheral {

    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.Set<HQSpeakerPeripheral>> COMPUTER_SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<HQSpeakerPeripheral> ACTIVE_SPEAKERS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final java.util.Set<IComputerAccess> attachedComputers = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final BlockPos pos;
    private final Level world;

    
    private static final int    SPEAKER_SAMPLE_RATE = 48_000;
    private static final int    SPEAKER_MAX_PCM     = 131_072;
    private static final int    SPEAKER_MAX_AUDIO   = 8 * 1024 * 1024;
    private static final int    SPEAKER_MAX_QUEUE   = 16;          
    private static final int    SPEAKER_READY_MARK  = 4;
    private static final long   SPEAKER_MIN_START_DELAY = 0L;

    private final UUID speakerSource = UUID.randomUUID();
    private final ArrayBlockingQueue<SpeakerChunk> speakerQueue = new ArrayBlockingQueue<>(SPEAKER_MAX_QUEUE);
    private final AtomicBoolean speakerReadyPending = new AtomicBoolean(false);
    /** NaN means "use the current server config default" for the next new source. */
    private volatile double speakerDefaultVolume = Double.NaN;
    private HQAudioTuningProfile rawTuning;
    private float rawLastRange;
    private float activeStopRange;
    private float streamRange;

    private final AtomicBoolean streamActive = new AtomicBoolean(false);
    private volatile String     streamUrl    = null;

    private volatile String icyTitle = "", icyArtist = "", icySong = "",
                            icyStationName = "", icyGenre = "", icyDescription = "";
    private volatile long   icyMetaSerial = 0;
    /** Invalidates blocking stream admissions which outlive a detach/cleanup lifecycle boundary. */
    private long lifecycleEpoch;

    private record SpeakerChunk(
        HQSpeakerAudioPacket.AudioFormat format, byte[] data,
        float volume, float gain, float range, boolean explicitRange,
        float deliveryRange, long startTick
    ) {}

    record PreparedTuning(HQAudioTuningProfile profile, HQAudioTuningProfile.Resolved resolved) {}

    public HQSpeakerPeripheral(BlockPos pos, Level world) {
        this.pos = pos;
        this.world = world;
    }

    @Nonnull @Override public String getType() { return "speaker"; }

    
    @LuaFunction public final String getPeripheralType() { return "speaker"; }

    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }

    @Override
    public void attach(@Nonnull IComputerAccess computer) {
        ACTIVE_SPEAKERS.add(this);
        attachedComputers.add(computer);
        COMPUTER_SPEAKERS.computeIfAbsent(computer.getID(), id -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(this);
        HQSpeakerMod.log("HQSpeaker attached to computer " + computer.getID() + " at " + pos);
    }

    @Override
    public void detach(@Nonnull IComputerAccess computer) {
        attachedComputers.remove(computer);
        var set = COMPUTER_SPEAKERS.get(computer.getID());
        if (set != null) {
            set.remove(this);
            if (set.isEmpty()) COMPUTER_SPEAKERS.remove(computer.getID(), set);
        }
        if (attachedComputers.isEmpty()) cleanup();
    }


    public static void tickAllActive() {
        for (HQSpeakerPeripheral p : ACTIVE_SPEAKERS) {
            try {
                if (p.world != null && !p.world.isClientSide && HQSpeakerPeripheralProvider.isComputerCraftSpeaker(p.world, p.pos)) {
                    p.speakerTick();
                } else {
                    p.cleanup();
                }
            } catch (Exception e) {
                HQSpeakerMod.warn("HQSpeaker tick failed at " + p.pos + ": " + e.getMessage());
            }
        }
    }

    public static java.util.List<HQSpeakerPeripheral> getSpeakersForComputer(int computerId) {
        var set = COMPUTER_SPEAKERS.get(computerId);
        if (set == null || set.isEmpty()) return java.util.List.of();

        java.util.ArrayList<HQSpeakerPeripheral> out = new java.util.ArrayList<>(set);
        out.sort(java.util.Comparator
            .comparingInt((HQSpeakerPeripheral p) -> p.pos.getX())
            .thenComparingInt(p -> p.pos.getY())
            .thenComparingInt(p -> p.pos.getZ()));
        return out;
    }


    private java.util.List<HQSpeakerPeripheral> membersFor(@Nullable IComputerAccess computer) {
        if (computer == null) return java.util.List.of(this);
        java.util.List<HQSpeakerPeripheral> members = getSpeakersForComputer(computer.getID());
        return members.isEmpty() ? java.util.List.of(this) : members;
    }

    private HQSpeakerPeripheral byIndexFor(@Nullable IComputerAccess computer, int index) throws LuaException {
        java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
        if (index < 1 || index > members.size()) throw new LuaException("speaker index out of range");
        return members.get(index - 1);
    }

    public synchronized void cleanup() {
        lifecycleEpoch++;
        ACTIVE_SPEAKERS.remove(this);
        float stopRange = activeStopRange;
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        broadcastStopPacket(stopRange);
        resetTuningState();
    }

    
    public void speakerTick() {
        SpeakerChunk chunk = speakerQueue.poll();
        if (chunk != null && world instanceof ServerLevel sl) {
            org.joml.Vector3d resolved = com.tom.hqspeaker.compat.MovingSourcePosition.resolve(
                world, pos, new org.joml.Vector3d());
            float wx = (float) resolved.x;
            float wy = (float) resolved.y;
            float wz = (float) resolved.z;

            var pkt = new HQSpeakerAudioPacket(
                speakerSource, chunk.format(),
                chunk.volume(), chunk.gain(), chunk.range(), chunk.explicitRange(),
                wx, wy, wz, pos.getX(), pos.getY(), pos.getZ(), chunk.data(), chunk.startTick()
            );
            sendToNearby(sl, pkt, wx, wy, wz, chunk.deliveryRange());
        }

        if (speakerQueue.size() < SPEAKER_READY_MARK) speakerReadyPending.set(true);

        if (speakerReadyPending.getAndSet(false)) {
            for (IComputerAccess comp : attachedComputers) comp.queueEvent("speaker_audio_empty", comp.getAttachmentName());
        }
    }

    
    static record PreparedPcm(
        byte[] data,
        float volume, float gain, float range, boolean explicitRange,
        HQAudioTuningProfile tuning,
        int samples
    ) {
        PreparedPcm {
            data = java.util.Arrays.copyOf(data, data.length);
        }
    }

    synchronized PreparedPcm preparePcm(IArguments args) throws LuaException {
        return preparePcm(args, 0, 1, 2);
    }

    synchronized PreparedPcm preparePcm(IArguments args, int dataIndex, int volumeIndex, int rangeIndex)
            throws LuaException {
        Map<?, ?> table = args.getTable(dataIndex);
        HQAudioTuningProfile tuning = rawTuning != null ? rawTuning : currentAudioProfile();
        Double requestedVolume = args.optDouble(volumeIndex).orElse(null);
        if (requestedVolume == null && Double.isFinite(speakerDefaultVolume)) requestedVolume = speakerDefaultVolume;
        Double requestedRange = args.optDouble(rangeIndex).orElse(null);
        HQAudioTuningProfile.Resolved resolved = resolve(tuning, requestedVolume, requestedRange);

        int len = 0;
        while ((table.containsKey((long)(len + 1)) || table.containsKey((double)(len + 1))) && len <= SPEAKER_MAX_PCM) len++;
        byte[] data = rawTableToPcmBytes(table, len, "speakPCM");
        return new PreparedPcm(
            data,
            resolved.logicalVolume(), resolved.gain(), resolved.range(), resolved.explicitRange(),
            tuning,
            len
        );
    }

    synchronized boolean enqueuePreparedPcmAtTick(PreparedPcm prepared, long startTick) {
        if (prepared == null) return false;
        if (rawTuning != null && rawTuning != prepared.tuning()) {
            throw new IllegalStateException("RAW source tuning profile changed during one source lifetime");
        }
        if (speakerQueue.size() >= SPEAKER_MAX_QUEUE) return false;

        float deliveryRange = Math.max(rawLastRange, prepared.range());
        byte[] safe = java.util.Arrays.copyOf(prepared.data(), prepared.data().length);
        SpeakerChunk chunk = new SpeakerChunk(
            HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, safe,
            prepared.volume(), prepared.gain(), prepared.range(), prepared.explicitRange(),
            deliveryRange, Math.max(0L, startTick)
        );
        if (!speakerQueue.offer(chunk)) return false;

        if (rawTuning == null) rawTuning = prepared.tuning();
        rawLastRange = prepared.range();
        activeStopRange = Math.max(activeStopRange, deliveryRange);
        if (speakerQueue.size() < SPEAKER_MAX_QUEUE) speakerReadyPending.set(true);
        return true;
    }

    long nextGroupStartTick() {
        return nextSyncedStartTick();
    }

    @LuaFunction
    public final void speakStop() {
        float stopRange = activeStopRange;
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        broadcastStopPacket(stopRange);
        resetTuningState();
        HQSpeakerMod.log("HQSpeaker: stopped at " + pos);
    }

    @LuaFunction
    public final void speakVolume(IArguments args) throws LuaException {
        double requested = args.getDouble(0);
        HQAudioTuningProfile tuning = currentAudioProfile();
        try {
            tuning.validateVolume(requested);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
        speakerDefaultVolume = requested;
    }

    float defaultVolume() throws LuaException {
        if (Double.isFinite(speakerDefaultVolume)) return (float) speakerDefaultVolume;
        return (float) currentAudioProfile().defaultVolume();
    }

    @LuaFunction
    public final boolean speakIsPlaying() {
        return streamActive.get() || !speakerQueue.isEmpty();
    }

    @LuaFunction public final int speakQueueSize() { return speakerQueue.size(); }
    @LuaFunction public final int speakSampleRate() { return SPEAKER_SAMPLE_RATE; }
    @LuaFunction public final int speakMaxAudioBytes() { return SPEAKER_MAX_AUDIO; }
    @LuaFunction public final String[] speakSupportedFiles() { return new String[]{"mp3", "wav"}; }


    @LuaFunction public final boolean isStreaming() { return streamActive.get(); }
    @LuaFunction
    public final Object[] getStreamUrl() {
        String url = streamUrl;
        return url == null ? new Object[0] : new Object[]{ url };
    }

    @LuaFunction
    public final Map<String, Object> getStreamFormats() {
        Map<String, Object> info = new HashMap<>();
        
        Map<String, Object> mp3 = new HashMap<>();
        mp3.put("name", "MP3/ICY Radio");
        mp3.put("method", "speakStream");
        mp3.put("extensions", new String[]{".mp3", ".mp2"});
        mp3.put("protocols", new String[]{"http", "https"});
        mp3.put("supportsICY", true);
        info.put("mp3", mp3);
        return info;
    }

    @LuaFunction public final Map<String, Object> getStreamMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("title", icyTitle); m.put("artist", icyArtist); m.put("song", icySong);
        m.put("station", icyStationName); m.put("genre", icyGenre); m.put("description", icyDescription);
        m.put("serial", icyMetaSerial); m.put("url", streamUrl != null ? streamUrl : "");
        return m;
    }

    @LuaFunction public final String getStreamTitle() { return icyTitle; }
    @LuaFunction public final String getStreamArtist() { return icyArtist; }
    @LuaFunction public final String getStreamSong() { return icySong; }
    @LuaFunction public final String getStreamStation() { return icyStationName; }
    @LuaFunction public final String getStreamGenre() { return icyGenre; }
    @LuaFunction public final long getStreamMetaSerial() { return icyMetaSerial; }

    public boolean canAcceptIcyMetadata(ServerPlayer player) {
        if (!streamActive.get() || player == null || player.level() != world) return false;
        float[] wp = computeWorldPos();
        double dx = player.getX() - wp[0], dy = player.getY() - wp[1], dz = player.getZ() - wp[2];
        float radius = streamRange;
        return radius > 0.0f && dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public void onIcyMetadata(String rawTitle, String stationName, String genre, String description) {
        icyStationName = stationName != null ? stationName : "";
        icyGenre = genre != null ? genre : "";
        icyDescription = description != null ? description : "";

        if (rawTitle == null) rawTitle = "";
        rawTitle = rawTitle.trim();
        icyTitle = rawTitle;

        int sep = rawTitle.indexOf(" - ");
        if (sep > 0) {
            icyArtist = rawTitle.substring(0, sep).trim();
            icySong = rawTitle.substring(sep + 3).trim();
        } else {
            icyArtist = ""; icySong = rawTitle;
        }
        icyMetaSerial++;

        for (IComputerAccess comp : attachedComputers) comp.queueEvent("hqspeaker_metadata", getStreamMeta());
    }

@LuaFunction
public final Map<String, Object> getPos() {
    return getPosMap();
}

public Map<String, Object> getPosMap() {
    Map<String, Object> out = new HashMap<>();
    out.put("x", pos.getX());
    out.put("y", pos.getY());
    out.put("z", pos.getZ());
    return out;
}

private static final long MULTI_SPEAKER_SYNC_LEAD_TICKS = 12L;

private long nextSyncedStartTick() {
    if (world instanceof ServerLevel sl) return sl.getGameTime() + MULTI_SPEAKER_SYNC_LEAD_TICKS;
    return 0L;
}

@LuaFunction
public final int getSpeakerCount(IComputerAccess computer) {
    return membersFor(computer).size();
}

@LuaFunction
public final java.util.List<java.util.Map<String, Object>> getSpeakers(IComputerAccess computer) {
    java.util.List<HQSpeakerPeripheral> ps = membersFor(computer);
    java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
    for (int i = 0; i < ps.size(); i++) {
        java.util.Map<String, Object> e = new java.util.HashMap<>(ps.get(i).getPosMap());
        e.put("index", i + 1);
        out.add(e);
    }
    return out;
}

@LuaFunction
public final java.util.Map<String, Object> getSpeakerPos(IComputerAccess computer, int index) throws LuaException {
    return byIndexFor(computer, index).getPosMap();
}


    private byte[] rawTableToPcmBytes(java.util.Map<?, ?> table, int len, String fnName) throws LuaException {
        if (len <= 0) throw new LuaException(fnName + ": table is empty");
        if (len > SPEAKER_MAX_PCM) throw new LuaException(fnName + ": table too large");
        ByteBuffer buf = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 1; i <= len; i++) {
            Object val = table.get((long) i);
            if (val == null) val = table.get((double) i);
            if (!(val instanceof Number n)) throw new LuaException(fnName + ": table[" + i + "] is not a number");
            double d = n.doubleValue();
            if (!Double.isFinite(d)) throw new LuaException(fnName + ": table[" + i + "] must be finite");
            int sample = (int) d;
            if (sample < -32768 || sample > 32767) {
                throw new LuaException(fnName + ": table[" + i + "] out of range");
            }
            buf.putShort((short) sample);
        }
        buf.flip();
        return buf.array();
    }

    synchronized long lifecycleEpochSnapshot() {
        return lifecycleEpoch;
    }

    synchronized boolean lifecycleEpochMatches(long expected) {
        return lifecycleEpoch == expected;
    }

    PreparedTuning prepareNewSourceTuning(Optional<Double> volume, Optional<Double> range) throws LuaException {
        return prepareNewSourceTuning(currentAudioProfile(), volume, range);
    }

    PreparedTuning prepareNewSourceTuning(
            HQAudioTuningProfile profile, Optional<Double> volume, Optional<Double> range) throws LuaException {
        Double requestedVolume = volume.orElse(null);
        if (requestedVolume == null && Double.isFinite(speakerDefaultVolume)) requestedVolume = speakerDefaultVolume;
        return new PreparedTuning(profile, resolve(profile, requestedVolume, range.orElse(null)));
    }

    boolean startValidatedStream(String url, PreparedTuning tuning,
                                 HQSpeakerAudioPacket.AudioFormat format,
                                 String method, long expectedLifecycle) throws LuaException {
        return startValidatedStreamAtTick(url, tuning, format, method, 0L, null, expectedLifecycle);
    }

    synchronized boolean startValidatedStreamAtTick(
            String url, PreparedTuning prepared,
            HQSpeakerAudioPacket.AudioFormat format, String method,
            long startTick, java.util.UUID syncGroupId, long expectedLifecycle) throws LuaException {
        if (lifecycleEpoch != expectedLifecycle) return false;
        if (prepared == null || prepared.profile() == null || prepared.resolved() == null) {
            throw new LuaException("audio tuning was not prepared");
        }
        HQAudioTuningProfile.Resolved resolved = prepared.resolved();

        speakStop();
        clearIcyMeta();
        if (startTick < 0L) startTick = 0L;

        if (world instanceof ServerLevel sl) {
            float[] wp = computeWorldPos();
            var pkt = new HQSpeakerAudioPacket(
                speakerSource, format,
                resolved.logicalVolume(), resolved.gain(), resolved.range(), resolved.explicitRange(),
                wp[0], wp[1], wp[2],
                pos.getX(), pos.getY(), pos.getZ(), url, startTick, syncGroupId);
            sendToNearby(sl, pkt, wp[0], wp[1], wp[2], resolved.range());
        }

        streamActive.set(true);
        streamUrl = url;
        streamRange = resolved.range();
        activeStopRange = resolved.range();
        IcyMetaPacket.SPEAKER_REGISTRY.put(speakerSource, this);
        HQSpeakerMod.log("HQSpeaker: started stream (" + method + ") from " + url
            + " volume=" + resolved.logicalVolume() + " gain=" + resolved.gain()
            + " range=" + resolved.range());
        return true;
    }

    static HQAudioTuningProfile currentServerAudioProfile() throws LuaException {
        try {
            return HQSpeakerServerConfig.audioProfile();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new LuaException("invalid HQ speaker server audio config: " + e.getMessage());
        }
    }

    private HQAudioTuningProfile currentAudioProfile() throws LuaException {
        return currentServerAudioProfile();
    }

    /*
     * Kept separate so active RAW sources can resolve later chunks against their original profile.
     */
    private HQAudioTuningProfile.Resolved resolve(
            HQAudioTuningProfile tuning, Double requestedVolume, Double requestedRange) throws LuaException {
        double volume = requestedVolume == null ? tuning.defaultVolume() : requestedVolume;
        try {
            return tuning.resolve(volume, requestedRange);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    private void resetTuningState() {
        rawTuning = null;
        rawLastRange = 0.0f;
        activeStopRange = 0.0f;
        streamRange = 0.0f;
    }

    private void clearIcyMeta() {
        icyTitle = icyArtist = icySong = "";
        icyStationName = icyGenre = icyDescription = "";
        icyMetaSerial++;
    }

    private float[] computeWorldPos() {
        org.joml.Vector3d resolved = com.tom.hqspeaker.compat.MovingSourcePosition.resolve(
            world, pos, new org.joml.Vector3d());
        return new float[]{(float) resolved.x, (float) resolved.y, (float) resolved.z};
    }

    private void sendToNearby(ServerLevel sl, CustomPacketPayload pkt,
                              float wx, float wy, float wz, float radius) {
        if (!(radius > 0.0f)) return;
        double radiusSquared = (double) radius * radius;
        for (ServerPlayer player : sl.players()) {
            double dx = player.getX() - wx, dy = player.getY() - wy, dz = player.getZ() - wz;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) HQSpeakerNetwork.sendToPlayer(pkt, player);
        }
    }

    private void broadcastStopPacket(float radius) {
        if (!(world instanceof ServerLevel sl) || !(radius > 0.0f)) return;
        var pkt = new HQSpeakerStopPacket(speakerSource);
        float[] wp = computeWorldPos();
        sendToNearby(sl, pkt, wp[0], wp[1], wp[2], radius);
    }

    static void validateStreamUrl(String url, String method) throws LuaException {
        try {
            com.tom.hqspeaker.network.StreamUrlPolicy.validate(url);
        } catch (java.io.IOException exception) {
            throw new LuaException(method + ": " + exception.getMessage());
        }
    }

}

