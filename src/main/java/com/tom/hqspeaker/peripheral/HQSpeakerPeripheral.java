package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
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
    private static final double SPEAKER_RADIUS      = 32.0;
    private static final long   SPEAKER_MIN_START_DELAY = 0L;
    private static final long   SPEAKER_MAX_START_DELAY = 20L * 60L; 

    private final UUID speakerSource = UUID.randomUUID();
    private final ArrayBlockingQueue<SpeakerChunk> speakerQueue = new ArrayBlockingQueue<>(SPEAKER_MAX_QUEUE);
    private final AtomicBoolean speakerReadyPending = new AtomicBoolean(false);
    private volatile float speakerDefaultVolume = 1.0f;

    private final AtomicBoolean streamActive = new AtomicBoolean(false);
    private volatile String     streamUrl    = null;

    private volatile String icyTitle = "", icyArtist = "", icySong = "",
                            icyStationName = "", icyGenre = "", icyDescription = "";
    private volatile long   icyMetaSerial = 0;
    /** Invalidates blocking stream admissions which outlive a detach/cleanup lifecycle boundary. */
    private long lifecycleEpoch;

    private record SpeakerChunk(HQSpeakerAudioPacket.AudioFormat format, byte[] data, float volume,
                                long startTick) {}

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
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        broadcastStopPacket();
    }

    
    public void speakerTick() {
        SpeakerChunk chunk = speakerQueue.poll();
        if (chunk != null && world instanceof ServerLevel sl) {
            float wx = pos.getX() + 0.5f;
            float wy = pos.getY() + 0.5f;
            float wz = pos.getZ() + 0.5f;

            try {
                if (com.tom.hqspeaker.vs2.VS2TransformHelper.isVS2Loaded()) {
                    Object ship = com.tom.hqspeaker.vs2.VS2TransformHelper.getShipManagingBlock(world, pos);
                    if (ship != null) {
                        org.joml.Matrix4dc mat = com.tom.hqspeaker.vs2.VS2TransformHelper.getShipToWorldMatrix(ship);
                        if (mat != null) {
                            org.joml.Vector3d v = new org.joml.Vector3d(wx, wy, wz);
                            mat.transformPosition(v);
                            wx = (float) v.x; wy = (float) v.y; wz = (float) v.z;
                        }
                    }
                }
            } catch (Exception e) {
                HQSpeakerMod.warn("HQSpeaker: VS2 conversion failed: " + e.getMessage());
            }

            var pkt = new HQSpeakerAudioPacket(
                speakerSource, chunk.format(), chunk.volume(),
                wx, wy, wz, pos.getX(), pos.getY(), pos.getZ(), chunk.data(), chunk.startTick()
            );

            final float fwx = wx, fwy = wy, fwz = wz;
            for (ServerPlayer player : sl.players()) {
                double dx = player.getX() - fwx, dy = player.getY() - fwy, dz = player.getZ() - fwz;
                if (dx*dx + dy*dy + dz*dz <= SPEAKER_RADIUS*SPEAKER_RADIUS) {
                    HQSpeakerNetwork.sendToPlayer(pkt, player);
                }
            }
        }

        if (speakerQueue.size() < SPEAKER_READY_MARK) speakerReadyPending.set(true);

        if (speakerReadyPending.getAndSet(false)) {
            for (IComputerAccess comp : attachedComputers) comp.queueEvent("speaker_audio_empty", comp.getAttachmentName());
        }
    }

    
    static record PreparedPcm(byte[] data, float volume, int samples) {
        PreparedPcm {
            data = java.util.Arrays.copyOf(data, data.length);
        }
    }

    PreparedPcm preparePcm(IArguments args) throws LuaException {
        return preparePcm(args, 0, 1);
    }

    PreparedPcm preparePcm(IArguments args, int dataIndex, int volumeIndex) throws LuaException {
        Map<?, ?> table = args.getTable(dataIndex);
        float volume = clampVolChecked(args.optDouble(volumeIndex, speakerDefaultVolume), "volume");
        int len = 0;
        while ((table.containsKey((long)(len + 1)) || table.containsKey((double)(len + 1))) && len <= SPEAKER_MAX_PCM) len++;
        byte[] data = rawTableToPcmBytes(table, len, "speakPCM");
        return new PreparedPcm(data, volume, len);
    }

    boolean enqueuePreparedPcmAtTick(PreparedPcm prepared, long startTick) {
        return prepared != null && enqueue(
            HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, prepared.data(), prepared.volume(), startTick);
    }

    long nextGroupStartTick() {
        return nextSyncedStartTick();
    }

    @LuaFunction
    public final void speakStop() {
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        broadcastStopPacket();
        HQSpeakerMod.log("HQSpeaker: stopped at " + pos);
    }

    @LuaFunction
    public final void speakVolume(IArguments args) throws LuaException {
        speakerDefaultVolume = clampVolChecked(args.getDouble(0), "volume");
    }

    float defaultVolume() {
        return speakerDefaultVolume;
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
    @LuaFunction public final Optional<String> getStreamUrl() { return Optional.ofNullable(streamUrl); }

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
        float[] wp = computeWorldPos("icyMeta");
        double dx = player.getX() - wp[0], dy = player.getY() - wp[1], dz = player.getZ() - wp[2];
        return dx * dx + dy * dy + dz * dz <= SPEAKER_RADIUS * SPEAKER_RADIUS;
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

    boolean startValidatedStream(String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format,
                                 String method, long expectedLifecycle) throws LuaException {
        return startValidatedStreamAtTick(url, volume, format, method, 0L, null, expectedLifecycle);
    }

    synchronized boolean startValidatedStreamAtTick(
            String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format, String method,
            long startTick, java.util.UUID syncGroupId, long expectedLifecycle) throws LuaException {
        if (lifecycleEpoch != expectedLifecycle) return false;
        float vol = clampVolChecked(volume.orElse((double) speakerDefaultVolume), "volume");
        speakStop();
        clearIcyMeta();

        if (startTick < 0L) startTick = 0L;

        if (world instanceof ServerLevel sl) {
            float[] wp = computeWorldPos(method);
            var pkt = new HQSpeakerAudioPacket(
                speakerSource, format, vol, wp[0], wp[1], wp[2],
                pos.getX(), pos.getY(), pos.getZ(), url, startTick, syncGroupId);
            sendToNearby(sl, pkt, wp[0], wp[1], wp[2]);
        }

        streamActive.set(true);
        streamUrl = url;
        IcyMetaPacket.SPEAKER_REGISTRY.put(speakerSource, this);
        HQSpeakerMod.log("HQSpeaker: started stream (" + method + ") from " + url);
        return true;
    }

    private boolean enqueue(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume,
                            long startTick) {
        // The legacy queue is now RAW-only. Finite MP3/WAV uses HQFiniteMediaServer; retired packed formats
        // must not recreate a second whole-file playback engine through this queue.
        if (fmt != HQSpeakerAudioPacket.AudioFormat.PCM_S16LE) return false;
        if (data == null || data.length == 0 || data.length > SPEAKER_MAX_AUDIO) return false;
        if (!Float.isFinite(volume)) volume = 1.0f;
        volume = Math.max(0.0f, Math.min(3.0f, volume));
        if (startTick < SPEAKER_MIN_START_DELAY) startTick = 0L;
        if (speakerQueue.size() >= SPEAKER_MAX_QUEUE) return false;

        byte[] safe = java.util.Arrays.copyOf(data, data.length);
        boolean offered = speakerQueue.offer(new SpeakerChunk(fmt, safe, volume, startTick));
        if (offered && speakerQueue.size() < SPEAKER_MAX_QUEUE) speakerReadyPending.set(true);
        return offered;
    }

    private static float clampVol(double v) {
        if (!Double.isFinite(v)) return 1.0f;
        return (float) Math.max(0.0, Math.min(3.0, v));
    }

    private static float clampVolChecked(double v, String name) throws LuaException {
        if (!Double.isFinite(v)) throw new LuaException(name + " must be finite");
        return (float) Math.max(0.0, Math.min(3.0, v));
    }

    private void clearIcyMeta() {
        icyTitle = icyArtist = icySong = "";
        icyStationName = icyGenre = icyDescription = "";
        icyMetaSerial++;
    }

    private float[] computeWorldPos(String ctx) {
        float wx = pos.getX() + 0.5f, wy = pos.getY() + 0.5f, wz = pos.getZ() + 0.5f;
        try {
            if (com.tom.hqspeaker.vs2.VS2TransformHelper.isVS2Loaded()) {
                Object ship = com.tom.hqspeaker.vs2.VS2TransformHelper.getShipManagingBlock(world, pos);
                if (ship != null) {
                    org.joml.Matrix4dc mat = com.tom.hqspeaker.vs2.VS2TransformHelper.getShipToWorldMatrix(ship);
                    if (mat != null) {
                        org.joml.Vector3d v = new org.joml.Vector3d(wx, wy, wz);
                        mat.transformPosition(v);
                        wx = (float) v.x; wy = (float) v.y; wz = (float) v.z;
                    }
                }
            }
        } catch (Exception e) { HQSpeakerMod.warn(ctx + ": VS2 transform failed: " + e.getMessage()); }
        return new float[]{wx, wy, wz};
    }

    private void sendToNearby(ServerLevel sl, CustomPacketPayload pkt, float wx, float wy, float wz) {
        for (ServerPlayer player : sl.players()) {
            double dx = player.getX() - wx, dy = player.getY() - wy, dz = player.getZ() - wz;
            if (dx*dx + dy*dy + dz*dz <= SPEAKER_RADIUS*SPEAKER_RADIUS)
                HQSpeakerNetwork.sendToPlayer(pkt, player);
        }
    }

    private void broadcastStopPacket() {
        if (!(world instanceof ServerLevel sl)) return;
        var pkt = new HQSpeakerStopPacket(speakerSource);
        float[] wp = computeWorldPos("broadcastStop");
        sendToNearby(sl, pkt, wp[0], wp[1], wp[2]);
    }

    static void validateStreamUrl(String url, String method) throws LuaException {
        try {
            com.tom.hqspeaker.network.StreamUrlPolicy.validate(url);
        } catch (java.io.IOException exception) {
            throw new LuaException(method + ": " + exception.getMessage());
        }
    }

}

