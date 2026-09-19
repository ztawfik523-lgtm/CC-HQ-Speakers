package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import com.tom.hqspeaker.network.HQSpeakerControlPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.network.HQSpeakerStatusPacket;
import com.tom.hqspeaker.network.HQSpeakerStopPacket;
import com.tom.hqspeaker.network.IcyMetaPacket;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
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
import java.util.concurrent.atomic.AtomicReference;


public class HQSpeakerPeripheral implements IPeripheral {

    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.Set<HQSpeakerPeripheral>> COMPUTER_SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<HQSpeakerPeripheral> ACTIVE_SPEAKERS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.concurrent.ConcurrentHashMap<UUID, HQSpeakerPeripheral> SOURCE_SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Set<IComputerAccess> attachedComputers = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final BlockPos pos;
    private final Level world;

    
    private static final int    SPEAKER_SAMPLE_RATE = 48_000;
    private static final int    SPEAKER_MAX_PCM     = 192_000;
    private static final int    SPEAKER_MAX_AUDIO   = 8 * 1024 * 1024;
    private static final int    SPEAKER_MAX_QUEUE   = 16;          
    private static final int    SPEAKER_READY_MARK  = 4;
    private static final double SPEAKER_RADIUS      = 32.0;
    private static final int    SPEAKER_MAX_AUDIO_TABLE = 131_072; 
    private static final int    SPEAKER_MAX_SYNC_GROUP = 64;
    private static final long   SPEAKER_MIN_START_DELAY = 0L;
    private static final long   SPEAKER_MAX_START_DELAY = 20L * 60L; 

    private final UUID speakerSource = UUID.randomUUID();
    private final ArrayBlockingQueue<SpeakerChunk> speakerQueue = new ArrayBlockingQueue<>(SPEAKER_MAX_QUEUE);
    private final AtomicBoolean speakerReadyPending = new AtomicBoolean(false);
    private volatile float speakerDefaultVolume = 1.0f;
    private volatile boolean looping = false;                     

    private final Object playerLock = new Object();
    private final ArrayDeque<FiniteServerTrack> finiteTracks = new ArrayDeque<>();
    private long generationCounter;
    private FiniteServerTrack terminalTrack;

    private final AtomicBoolean streamActive = new AtomicBoolean(false);
    private volatile String     streamUrl    = null;

    private volatile String icyTitle = "", icyArtist = "", icySong = "",
                            icyStationName = "", icyGenre = "", icyDescription = "";
    private volatile long   icyMetaSerial = 0;
    /** Invalidates blocking stream admissions which outlive a detach/cleanup lifecycle boundary. */
    private long lifecycleEpoch;

    private record SpeakerChunk(HQSpeakerAudioPacket.AudioFormat format, byte[] data, float volume,
                                long startTick, java.util.UUID syncGroupId, int syncGroupSize,
                                long generation) {}

    private enum PlayerState { LOADING, PLAYING, PAUSED, ENDED, ERROR }

    private static final class FiniteServerTrack {
        final long generation;
        final HQSpeakerAudioPacket.AudioFormat format;
        final Set<UUID> successfulRenderers = new HashSet<>();
        float volume;
        boolean looping;
        boolean desiredPaused;
        boolean observed;
        PlayerState state = PlayerState.LOADING;
        double duration;
        double basePosition;
        long anchorNanos;
        UUID anchorRenderer;
        String error = "";

        FiniteServerTrack(long generation, HQSpeakerAudioPacket.AudioFormat format,
                          float volume, boolean looping) {
            this.generation = generation;
            this.format = format;
            this.volume = volume;
            this.looping = looping;
        }

        double position(long now) {
            double position = basePosition;
            if (state == PlayerState.PLAYING && observed) {
                position += Math.max(0L, now - anchorNanos) / 1_000_000_000.0;
            }
            if (duration > 0.0) {
                if (looping) position %= duration;
                else position = Math.min(position, duration);
            }
            return Math.max(0.0, position);
        }
    }

    public HQSpeakerPeripheral(BlockPos pos, Level world) {
        this.pos = pos;
        this.world = world;
        SOURCE_SPEAKERS.put(speakerSource, this);
    }

    public static HQSpeakerPeripheral findBySource(UUID source) {
        return source == null ? null : SOURCE_SPEAKERS.get(source);
    }

    @Nonnull @Override public String getType() { return "speaker"; }

    
    @LuaFunction public final String getPeripheralType() { return "speaker"; }

    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }

    @Override
    public void attach(@Nonnull IComputerAccess computer) {
        ACTIVE_SPEAKERS.add(this);
        SOURCE_SPEAKERS.put(speakerSource, this);
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

    private HQSpeakerPeripheral leaderFor(@Nullable IComputerAccess computer) throws LuaException {
        java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
        if (members.isEmpty()) throw new LuaException("no speakers connected to this computer");
        return members.get(0);
    }

    private HQSpeakerPeripheral byIndexFor(@Nullable IComputerAccess computer, int index) throws LuaException {
        java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
        if (index < 1 || index > members.size()) throw new LuaException("speaker index out of range");
        return members.get(index - 1);
    }

    private static boolean anyTrue(boolean current, boolean next) {
        return current || next;
    }

    public synchronized void cleanup() {
        lifecycleEpoch++;
        ACTIVE_SPEAKERS.remove(this);
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        SOURCE_SPEAKERS.remove(speakerSource, this);
        clearFiniteState(true);
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

            float packetVolume = chunk.volume();
            boolean packetLooping = false;
            boolean packetPaused = false;
            if (chunk.generation() > 0L) {
                synchronized (playerLock) {
                    FiniteServerTrack finite = findFiniteTrackLocked(chunk.generation());
                    if (finite != null) {
                        packetVolume = finite.volume;
                        packetLooping = finite.looping;
                        packetPaused = finite.desiredPaused;
                    }
                }
            }
            var pkt = new HQSpeakerAudioPacket(
                speakerSource, chunk.format(), packetVolume,
                wx, wy, wz, pos.getX(), pos.getY(), pos.getZ(), chunk.data(),
                chunk.startTick(), chunk.syncGroupId(), chunk.syncGroupSize(),
                chunk.generation(), packetLooping, packetPaused
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

    
    @LuaFunction
    public final boolean playNote(String instrument, double volume, double pitch) throws LuaException {
        
        float vol = clampVolChecked(volume, "volume");
        if (!Double.isFinite(pitch)) throw new LuaException("pitch must be finite");
        pitch = Math.max(0.0, Math.min(24.0, pitch));

        int samples = (int)(SPEAKER_SAMPLE_RATE * 0.8); 
        float freq = (float)(440.0 * Math.pow(2.0, (pitch - 9.0) / 12.0));

        ByteBuffer buf = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * freq * i / SPEAKER_SAMPLE_RATE;
            short sample = (short)(Math.sin(angle) * 32767 * 0.6);
            buf.putShort(sample);
        }
        buf.flip();
        return enqueue(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, buf.array(), vol);
    }

    @LuaFunction
    public final boolean playSound(String soundName, Optional<Double> volume, Optional<Double> pitch) throws LuaException {
        return playNote("harp", volume.orElse(1.0), pitch.orElse(1.0));
    }

    
    static record PreparedPcm(byte[] data, float volume, int samples) {
        PreparedPcm {
            data = java.util.Arrays.copyOf(data, data.length);
        }
    }

    PreparedPcm preparePcm(IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0);
        float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
        int len = 0;
        while ((table.containsKey((long)(len + 1)) || table.containsKey((double)(len + 1))) && len <= SPEAKER_MAX_PCM) len++;
        byte[] data = audioTableToPcmBytes(table, len, "speakPCM", -32768, 32767, 16);
        return new PreparedPcm(data, volume, len);
    }

    boolean enqueuePreparedPcm(PreparedPcm prepared) {
        return prepared != null && enqueue(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, prepared.data(), prepared.volume());
    }

    @LuaFunction
    public final boolean speakPCM(IArguments args) throws LuaException {
        return enqueuePreparedPcm(preparePcm(args));
    }

    @LuaFunction public final boolean speakOgg(IArguments args) throws LuaException { return speakAudioFile(args, HQSpeakerAudioPacket.AudioFormat.OGG_VORBIS, "speakOgg"); }
    @LuaFunction public final boolean speakMp3(IArguments args) throws LuaException { return speakAudioFile(args, HQSpeakerAudioPacket.AudioFormat.MP3, "speakMp3"); }
    @LuaFunction public final boolean speakAudio(IArguments args) throws LuaException { return speakAudioFile(args, HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE, "speakAudio"); }
    @LuaFunction public final boolean speakFile(IArguments args) throws LuaException { return speakAudio(args); }
    @LuaFunction public final boolean speakPacked(IArguments args) throws LuaException { return speakAudio(args); }

    @LuaFunction
    public final boolean speakWav(IArguments args) throws LuaException {
        ByteBuffer dataBuf = args.getBytes(0);
        byte[] data = new byte[dataBuf.remaining()];
        dataBuf.duplicate().get(data);
        float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
        if (data.length == 0) throw new LuaException("speakWav: data is empty");
        if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException("speakWav: file too large");
        return enqueue(HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE, data, volume);
    }

    @LuaFunction
    public final void speakStop() {
        speakerQueue.clear();
        speakerReadyPending.set(false);
        streamActive.set(false);
        streamUrl = null;
        IcyMetaPacket.SPEAKER_REGISTRY.remove(speakerSource);
        clearFiniteState(true);
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
    public final void setLooping(boolean loop) {
        looping = loop;
        FiniteServerTrack current;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current != null) current.looping = loop;
        }
        if (current != null) sendControl(current,
            HQSpeakerControlPacket.Action.SET_LOOP, loop ? 1.0 : 0.0);
        HQSpeakerMod.log("HQSpeaker: looping set to " + loop + " at " + pos);
    }

    @LuaFunction
    public final boolean speakIsPlaying() {
        if (streamActive.get()) return true;
        synchronized (playerLock) {
            FiniteServerTrack current = finiteTracks.peekFirst();
            if (current != null) {
                return current.state == PlayerState.LOADING
                    || current.state == PlayerState.PLAYING
                    || current.state == PlayerState.PAUSED;
            }
        }
        return !speakerQueue.isEmpty();
    }

    @LuaFunction
    public final Map<String, Object> audioStatus() {
        synchronized (playerLock) {
            FiniteServerTrack current = finiteTracks.peekFirst();
            if (current != null) return finiteStatus(current);
            if (terminalTrack != null) return finiteStatus(terminalTrack);
        }
        Map<String, Object> status = new HashMap<>();
        if (streamActive.get()) {
            status.put("state", "playing");
            status.put("kind", "stream");
            status.put("observed", false);
        } else {
            status.put("state", "idle");
            status.put("kind", "none");
            status.put("observed", false);
        }
        status.put("canPause", false);
        status.put("canSeek", false);
        status.put("canLoop", false);
        return status;
    }

    @LuaFunction public final boolean audioPause() {
        FiniteServerTrack current;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current == null || current.state == PlayerState.ENDED
                    || current.state == PlayerState.ERROR) return false;
            if (current.desiredPaused) return true;
            current.basePosition = current.position(System.nanoTime());
            current.desiredPaused = true;
            current.state = PlayerState.PAUSED;
        }
        sendControl(current, HQSpeakerControlPacket.Action.PAUSE, 0.0);
        return true;
    }

    @LuaFunction public final boolean audioResume() {
        FiniteServerTrack current;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current == null || current.state == PlayerState.ENDED
                    || current.state == PlayerState.ERROR) return false;
            if (!current.desiredPaused) return true;
            current.desiredPaused = false;
            current.anchorNanos = System.nanoTime();
            current.state = current.observed ? PlayerState.PLAYING : PlayerState.LOADING;
        }
        sendControl(current, HQSpeakerControlPacket.Action.RESUME, 0.0);
        return true;
    }

    @LuaFunction public final boolean audioSeek(double seconds) throws LuaException {
        if (!Double.isFinite(seconds)) throw new LuaException("seconds must be finite");
        FiniteServerTrack current;
        double target;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current == null || current.duration <= 0.0
                    || current.state == PlayerState.ERROR || current.state == PlayerState.ENDED) return false;
            target = Math.max(0.0, Math.min(current.duration, seconds));
            current.basePosition = target;
            current.anchorNanos = System.nanoTime();
        }
        sendControl(current, HQSpeakerControlPacket.Action.SEEK, target);
        return true;
    }

    @LuaFunction public final boolean audioSetVolume(double volume) throws LuaException {
        float applied = clampVolChecked(volume, "volume");
        FiniteServerTrack current;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current == null) return false;
            current.volume = applied;
        }
        sendControl(current, HQSpeakerControlPacket.Action.SET_VOLUME, applied);
        return true;
    }

    @LuaFunction public final boolean audioSetLooping(boolean loop) {
        looping = loop;
        FiniteServerTrack current;
        synchronized (playerLock) {
            current = finiteTracks.peekFirst();
            if (current == null) return false;
            current.looping = loop;
        }
        sendControl(current, HQSpeakerControlPacket.Action.SET_LOOP, loop ? 1.0 : 0.0);
        return true;
    }

    @LuaFunction public final void audioStop() { speakStop(); }
    @LuaFunction public final int speakQueueSize() { return speakerQueue.size(); }
    @LuaFunction public final int speakSampleRate() { return SPEAKER_SAMPLE_RATE; }
    @LuaFunction public final int speakMaxSamples() { return SPEAKER_MAX_PCM; }
    @LuaFunction public final int speakMaxAudioBytes() { return SPEAKER_MAX_AUDIO; }
    @LuaFunction public final int speakMaxOggBytes() { return SPEAKER_MAX_AUDIO; }
    @LuaFunction public final int speakMaxFileBytes() { return SPEAKER_MAX_AUDIO; }
    @LuaFunction public final String[] speakSupportedFiles() { return new String[]{"wav", "ogg", "mp3", "aiff", "aif", "au", "snd", "mp2", "mp4", "m4a", "aac"}; }

    @LuaFunction public final boolean speakStream(String url, Optional<Double> volume) throws LuaException { return startStream(url, volume, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM, "speakStream"); }
    @LuaFunction public final boolean speakHLS(String url, Optional<Double> volume) throws LuaException { return startStream(url, volume, HQSpeakerAudioPacket.AudioFormat.HLS_STREAM, "speakHLS"); }
    @LuaFunction public final boolean speakTS(String url, Optional<Double> volume) throws LuaException { return startStream(url, volume, HQSpeakerAudioPacket.AudioFormat.TS_STREAM, "speakTS"); }

    @LuaFunction public final boolean isStreaming() { return streamActive.get(); }
    @LuaFunction public final Optional<String> getStreamUrl() { return Optional.ofNullable(streamUrl); }

    @LuaFunction
    public final Map<String, Object> getStreamFormats() {
        Map<String, Object> info = new HashMap<>();
        
        Map<String, Object> mp3 = new HashMap<>(); mp3.put("name","MP3 Stream"); mp3.put("method","speakStream"); mp3.put("extensions",new String[]{".mp3",".mp2"}); mp3.put("protocols",new String[]{"http","https"}); mp3.put("supportsICY",true);
        Map<String, Object> hls = new HashMap<>(); hls.put("name","HLS Stream"); hls.put("method","speakHLS"); hls.put("extensions",new String[]{".m3u8",".m3u"}); hls.put("protocols",new String[]{"http","https"}); hls.put("supportsLive",true); hls.put("supportsVOD",true);
        Map<String, Object> ts = new HashMap<>(); ts.put("name","MPEG-TS Stream"); ts.put("method","speakTS"); ts.put("extensions",new String[]{".ts"}); ts.put("protocols",new String[]{"http","https"}); ts.put("audioCodecs",new String[]{"AAC","MP3"});
        info.put("mp3",mp3); info.put("hls",hls); info.put("ts",ts);
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

    public void acceptPlaybackStatus(ServerPlayer sender, HQSpeakerStatusPacket packet) {
        if (sender == null || packet == null || !speakerSource.equals(packet.source)
                || !packet.hasSensibleNumbers() || !canAcceptPlaybackStatus(sender)) return;

        boolean changed = false;
        synchronized (playerLock) {
            FiniteServerTrack track = findFiniteTrackLocked(packet.generation);
            if (track == null) return;
            long now = System.nanoTime();
            UUID renderer = sender.getUUID();
            switch (packet.transition) {
                case READY -> {
                    if (packet.duration > 0.0) track.duration = packet.duration;
                    changed = true;
                }
                case STARTED -> {
                    promoteLocked(track);
                    if (track.successfulRenderers.size() < 8) track.successfulRenderers.add(renderer);
                    if (track.anchorRenderer == null) track.anchorRenderer = renderer;
                    track.observed = true;
                    if (renderer.equals(track.anchorRenderer)) {
                        track.basePosition = clampPosition(track, packet.position);
                        track.anchorNanos = now;
                        track.state = track.desiredPaused ? PlayerState.PAUSED : PlayerState.PLAYING;
                    }
                    changed = true;
                }
                case PAUSED -> {
                    if (!isAnchor(track, renderer)) return;
                    track.observed = true;
                    track.desiredPaused = true;
                    track.basePosition = clampPosition(track, packet.position);
                    track.state = PlayerState.PAUSED;
                    changed = true;
                }
                case RESUMED -> {
                    if (!isAnchor(track, renderer)) return;
                    track.observed = true;
                    track.desiredPaused = false;
                    track.basePosition = clampPosition(track, packet.position);
                    track.anchorNanos = now;
                    track.state = PlayerState.PLAYING;
                    changed = true;
                }
                case SEEKED -> {
                    if (!isAnchor(track, renderer)) return;
                    track.observed = true;
                    track.basePosition = clampPosition(track, packet.position);
                    track.anchorNanos = now;
                    track.state = track.desiredPaused ? PlayerState.PAUSED : PlayerState.PLAYING;
                    changed = true;
                }
                case ENDED -> {
                    if (!isAnchor(track, renderer) || finiteTracks.peekFirst() != track) return;
                    track.observed = true;
                    track.basePosition = track.duration > 0.0 ? track.duration : packet.position;
                    track.state = PlayerState.ENDED;
                    finiteTracks.removeFirst();
                    terminalTrack = finiteTracks.isEmpty() ? track : null;
                    changed = true;
                }
                case ERROR -> {
                    boolean anotherSucceeded = track.successfulRenderers.stream()
                        .anyMatch(id -> !id.equals(renderer));
                    if (anotherSucceeded || (track.anchorRenderer != null
                            && !track.anchorRenderer.equals(renderer) && track.observed)) return;
                    track.basePosition = clampPosition(track, packet.position);
                    track.state = PlayerState.ERROR;
                    track.error = packet.error.isBlank() ? "client playback error" : packet.error;
                    if (finiteTracks.peekFirst() == track && finiteTracks.size() == 1) {
                        terminalTrack = track;
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            Map<String, Object> status = audioStatus();
            for (IComputerAccess computer : attachedComputers) {
                computer.queueEvent("hqspeaker_audio_state", status);
            }
        }
    }

    private boolean canAcceptPlaybackStatus(ServerPlayer player) {
        if (player.level() != world) return false;
        float[] worldPos = computeWorldPos("playerStatus");
        double dx = player.getX() - worldPos[0];
        double dy = player.getY() - worldPos[1];
        double dz = player.getZ() - worldPos[2];
        return dx * dx + dy * dy + dz * dz <= SPEAKER_RADIUS * SPEAKER_RADIUS;
    }

    private static boolean isAnchor(FiniteServerTrack track, UUID renderer) {
        if (track.anchorRenderer == null) track.anchorRenderer = renderer;
        return renderer.equals(track.anchorRenderer);
    }

    private static double clampPosition(FiniteServerTrack track, double position) {
        if (!Double.isFinite(position)) return 0.0;
        return track.duration > 0.0
            ? Math.max(0.0, Math.min(track.duration, position)) : Math.max(0.0, position);
    }

    private void promoteLocked(FiniteServerTrack track) {
        while (!finiteTracks.isEmpty() && finiteTracks.peekFirst() != track) {
            finiteTracks.removeFirst();
        }
        terminalTrack = null;
    }

    private FiniteServerTrack findFiniteTrackLocked(long generation) {
        for (FiniteServerTrack track : finiteTracks) {
            if (track.generation == generation) return track;
        }
        return null;
    }

    private Map<String, Object> finiteStatus(FiniteServerTrack track) {
        Map<String, Object> status = new HashMap<>();
        status.put("generation", track.generation);
        status.put("state", track.state.name().toLowerCase(Locale.ROOT));
        status.put("kind", "finite");
        status.put("format", switch (track.format) {
            case OGG_VORBIS -> "ogg";
            case MP3 -> "mp3";
            default -> "audio";
        });
        status.put("position", track.position(System.nanoTime()));
        if (track.duration > 0.0) status.put("duration", track.duration);
        status.put("volume", (double) track.volume);
        status.put("looping", track.looping);
        status.put("observed", track.observed);
        status.put("canPause", track.state != PlayerState.ENDED && track.state != PlayerState.ERROR);
        status.put("canSeek", track.duration > 0.0
            && track.state != PlayerState.ENDED && track.state != PlayerState.ERROR);
        status.put("canLoop", track.state != PlayerState.ENDED && track.state != PlayerState.ERROR);
        status.put("queueSize", finiteTracks.size());
        if (!track.error.isBlank()) status.put("error", track.error);
        return status;
    }

    private void clearFiniteState(boolean invalidateGeneration) {
        synchronized (playerLock) {
            finiteTracks.clear();
            terminalTrack = null;
            if (invalidateGeneration) generationCounter++;
        }
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

@LuaFunction
public final boolean playAudio(IArguments args) throws LuaException {
    Map<?, ?> table = args.getTable(0);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");

    int len = 0;
    while ((table.containsKey((long) (len + 1)) || table.containsKey((double) (len + 1))) && len <= SPEAKER_MAX_AUDIO_TABLE) len++;
    if (len <= 0) throw new LuaException("playAudio: table is empty");
    if (len > SPEAKER_MAX_AUDIO_TABLE) throw new LuaException("playAudio: table too large");
    return playAudioTable(table, len, volume);
}

public boolean playAudioTable(Map<?, ?> table, int len, float volume) throws LuaException {
    return enqueue(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, audioTableToPcmBytes(table, len, "playAudio", -128, 127, 8), volume);
}

public boolean speakPCMTable(Map<?, ?> table, int len, float volume) throws LuaException {
    return enqueue(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, audioTableToPcmBytes(table, len, "speakPCM", -32768, 32767, 16), volume);
}

public boolean enqueueAudio(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume) {
    return enqueue(fmt, data, volume);
}

public boolean enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume, long startTick) {
    return enqueue(fmt, data, volume, startTick);
}

public boolean enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume, long startTick, java.util.UUID syncGroupId, int syncGroupSize) {
    return enqueue(fmt, data, volume, startTick, syncGroupId, syncGroupSize);
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


private record SyncDispatch(long startTick, java.util.UUID syncGroupId, int syncGroupSize) {}

private SyncDispatch nextSyncDispatch(int members) {
    if (members <= 1) return new SyncDispatch(0L, null, 0);
    return new SyncDispatch(nextSyncedStartTick(), java.util.UUID.randomUUID(), members);
}

@LuaFunction
public final boolean playNoteAll(IComputerAccess computer, String instrument, double volume, double pitch) throws LuaException {
    boolean ok = false;
    float vol = clampVolChecked(volume, "volume");
    int samples = (int)(SPEAKER_SAMPLE_RATE * 0.8);
    float freq = (float)(440.0 * Math.pow(2.0, (pitch - 9.0) / 12.0));
    ByteBuffer buf = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < samples; i++) {
        double angle = 2 * Math.PI * freq * i / SPEAKER_SAMPLE_RATE;
        short sample = (short)(Math.sin(angle) * 32767 * 0.6);
        buf.putShort(sample);
    }
    buf.flip();
    byte[] audio = buf.array();
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, audio, vol, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean playSoundAll(IComputerAccess computer, String soundName, java.util.Optional<Double> volume, java.util.Optional<Double> pitch) throws LuaException {
    return playNoteAll(computer, "harp", volume.orElse(1.0), pitch.orElse(1.0));
}

@LuaFunction
public final boolean playAudioAll(IComputerAccess computer, IArguments args) throws LuaException {
    java.util.Map<?, ?> table = args.getTable(0);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    int len = 0;
    while ((table.containsKey((long) (len + 1)) || table.containsKey((double) (len + 1))) && len <= SPEAKER_MAX_AUDIO_TABLE) len++;
    boolean ok = false;
    byte[] pcm = audioTableToPcmBytes(table, len, "playAudio", -128, 127, 8);
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, pcm, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakPCMAll(IComputerAccess computer, IArguments args) throws LuaException {
    java.util.Map<?, ?> table = args.getTable(0);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    int len = 0;
    while ((table.containsKey((long) (len + 1)) || table.containsKey((double) (len + 1))) && len <= SPEAKER_MAX_AUDIO_TABLE) len++;
    boolean ok = false;
    byte[] pcm = audioTableToPcmBytes(table, len, "speakPCM", -32768, 32767, 16);
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.PCM_S16LE, pcm, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakOggAll(IComputerAccess computer, IArguments args) throws LuaException {
    ByteBuffer dataBuf = args.getBytes(0);
    byte[] data = new byte[dataBuf.remaining()];
    dataBuf.duplicate().get(data);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    if (data.length == 0) throw new LuaException("speakOgg: data is empty");
    if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException("speakOgg: file too large (max " + (SPEAKER_MAX_AUDIO / 1024 / 1024) + " MB)");
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.OGG_VORBIS, data, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakMp3All(IComputerAccess computer, IArguments args) throws LuaException {
    ByteBuffer dataBuf = args.getBytes(0);
    byte[] data = new byte[dataBuf.remaining()];
    dataBuf.duplicate().get(data);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    if (data.length == 0) throw new LuaException("speakMp3: data is empty");
    if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException("speakMp3: file too large (max " + (SPEAKER_MAX_AUDIO / 1024 / 1024) + " MB)");
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.MP3, data, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakAudioAll(IComputerAccess computer, IArguments args) throws LuaException {
    ByteBuffer dataBuf = args.getBytes(0);
    byte[] data = new byte[dataBuf.remaining()];
    dataBuf.duplicate().get(data);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    if (data.length == 0) throw new LuaException("speakAudio: data is empty");
    if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException("speakAudio: file too large (max " + (SPEAKER_MAX_AUDIO / 1024 / 1024) + " MB)");
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE, data, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction public final boolean speakFileAll(IComputerAccess computer, IArguments args) throws LuaException { return speakAudioAll(computer, args); }
@LuaFunction public final boolean speakPackedAll(IComputerAccess computer, IArguments args) throws LuaException { return speakAudioAll(computer, args); }


@LuaFunction
public final boolean speakWavAll(IComputerAccess computer, IArguments args) throws LuaException {
    ByteBuffer dataBuf = args.getBytes(0);
    byte[] data = new byte[dataBuf.remaining()];
    dataBuf.duplicate().get(data);
    float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");
    if (data.length == 0) throw new LuaException("speakWav: data is empty");
    if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException("speakWav: file too large");
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.enqueueAudioAtTick(HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE, data, volume, sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final void speakStopAll(IComputerAccess computer) {
    for (HQSpeakerPeripheral p : membersFor(computer)) p.speakStop();
}

@LuaFunction
public final void speakVolumeAll(IComputerAccess computer, IArguments args) throws LuaException {
    for (HQSpeakerPeripheral p : membersFor(computer)) p.speakVolume(args);
}

@LuaFunction
public final void setLoopingAll(IComputerAccess computer, boolean loop) {
    for (HQSpeakerPeripheral p : membersFor(computer)) p.setLooping(loop);
}

@LuaFunction
public final Map<String, Object> audioStatusAll(IComputerAccess computer) throws LuaException {
    return leaderFor(computer).audioStatus();
}

@LuaFunction
public final boolean audioPauseAll(IComputerAccess computer) {
    boolean ok = false;
    for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.audioPause());
    return ok;
}

@LuaFunction
public final boolean audioResumeAll(IComputerAccess computer) {
    boolean ok = false;
    for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.audioResume());
    return ok;
}

@LuaFunction
public final boolean audioSeekAll(IComputerAccess computer, double seconds) throws LuaException {
    boolean ok = false;
    for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.audioSeek(seconds));
    return ok;
}

@LuaFunction
public final boolean audioSetVolumeAll(IComputerAccess computer, double volume) throws LuaException {
    boolean ok = false;
    for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.audioSetVolume(volume));
    return ok;
}

@LuaFunction
public final boolean audioSetLoopingAll(IComputerAccess computer, boolean loop) {
    boolean ok = false;
    for (HQSpeakerPeripheral p : membersFor(computer)) ok = anyTrue(ok, p.audioSetLooping(loop));
    return ok;
}

@LuaFunction
public final void audioStopAll(IComputerAccess computer) {
    for (HQSpeakerPeripheral p : membersFor(computer)) p.audioStop();
}

@LuaFunction
public final boolean speakStreamAll(IComputerAccess computer, String url, java.util.Optional<Double> volume) throws LuaException {
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.startStreamAtTick(url, volume, HQSpeakerAudioPacket.AudioFormat.MP3_STREAM, "speakStream", sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakHLSAll(IComputerAccess computer, String url, java.util.Optional<Double> volume) throws LuaException {
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.startStreamAtTick(url, volume, HQSpeakerAudioPacket.AudioFormat.HLS_STREAM, "speakHLS", sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean speakTSAll(IComputerAccess computer, String url, java.util.Optional<Double> volume) throws LuaException {
    boolean ok = false;
    java.util.List<HQSpeakerPeripheral> members = membersFor(computer);
    SyncDispatch sync = nextSyncDispatch(members.size());
    for (HQSpeakerPeripheral p : members) ok = anyTrue(ok, p.startStreamAtTick(url, volume, HQSpeakerAudioPacket.AudioFormat.TS_STREAM, "speakTS", sync.startTick(), sync.syncGroupId(), sync.syncGroupSize()));
    return ok;
}

@LuaFunction
public final boolean playNoteAt(IComputerAccess computer, int index, String instrument, double volume, double pitch) throws LuaException {
    return byIndexFor(computer, index).playNote(instrument, volume, pitch);
}

@LuaFunction
public final boolean playSoundAt(IComputerAccess computer, int index, String soundName, java.util.Optional<Double> volume, java.util.Optional<Double> pitch) throws LuaException {
    return byIndexFor(computer, index).playSound(soundName, volume, pitch);
}

@LuaFunction
public final boolean playAudioAt(IComputerAccess computer, int index, java.util.Map<?, ?> audio, java.util.Optional<Double> volume) throws LuaException {
    int len = 0;
    while ((audio.containsKey((long) (len + 1)) || audio.containsKey((double) (len + 1))) && len <= SPEAKER_MAX_AUDIO_TABLE) len++;
    return byIndexFor(computer, index).playAudioTable(audio, len, clampVolChecked(volume.orElse((double) speakerDefaultVolume), "volume"));
}

@LuaFunction
public final boolean speakPCMAt(IComputerAccess computer, int index, java.util.Map<?, ?> audio, java.util.Optional<Double> volume) throws LuaException {
    int len = 0;
    while ((audio.containsKey((long) (len + 1)) || audio.containsKey((double) (len + 1))) && len <= SPEAKER_MAX_AUDIO_TABLE) len++;
    return byIndexFor(computer, index).speakPCMTable(audio, len, clampVolChecked(volume.orElse((double) speakerDefaultVolume), "volume"));
}

@LuaFunction
public final boolean speakStreamAt(IComputerAccess computer, int index, String url, java.util.Optional<Double> volume) throws LuaException {
    return byIndexFor(computer, index).speakStream(url, volume);
}

@LuaFunction
public final boolean speakHLSAt(IComputerAccess computer, int index, String url, java.util.Optional<Double> volume) throws LuaException {
    return byIndexFor(computer, index).speakHLS(url, volume);
}

@LuaFunction
public final boolean speakTSAt(IComputerAccess computer, int index, String url, java.util.Optional<Double> volume) throws LuaException {
    return byIndexFor(computer, index).speakTS(url, volume);
}

@LuaFunction
public final boolean speakMp3At(IComputerAccess computer, int index, IArguments args) throws LuaException {
    return byIndexFor(computer, index).speakMp3(args);
}

@LuaFunction
public final boolean speakOggAt(IComputerAccess computer, int index, IArguments args) throws LuaException {
    return byIndexFor(computer, index).speakOgg(args);
}

@LuaFunction
public final boolean speakAudioAt(IComputerAccess computer, int index, IArguments args) throws LuaException {
    return byIndexFor(computer, index).speakAudio(args);
}

@LuaFunction public final boolean speakFileAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return speakAudioAt(computer, index, args); }
@LuaFunction public final boolean speakPackedAt(IComputerAccess computer, int index, IArguments args) throws LuaException { return speakAudioAt(computer, index, args); }


@LuaFunction
public final boolean speakWavAt(IComputerAccess computer, int index, IArguments args) throws LuaException {
    return byIndexFor(computer, index).speakWav(args);
}

@LuaFunction
public final void speakStopAt(IComputerAccess computer, int index) throws LuaException {
    byIndexFor(computer, index).speakStop();
}

@LuaFunction public final Map<String, Object> audioStatusAt(IComputerAccess computer, int index) throws LuaException { return byIndexFor(computer, index).audioStatus(); }
@LuaFunction public final boolean audioPauseAt(IComputerAccess computer, int index) throws LuaException { return byIndexFor(computer, index).audioPause(); }
@LuaFunction public final boolean audioResumeAt(IComputerAccess computer, int index) throws LuaException { return byIndexFor(computer, index).audioResume(); }
@LuaFunction public final boolean audioSeekAt(IComputerAccess computer, int index, double seconds) throws LuaException { return byIndexFor(computer, index).audioSeek(seconds); }
@LuaFunction public final boolean audioSetVolumeAt(IComputerAccess computer, int index, double volume) throws LuaException { return byIndexFor(computer, index).audioSetVolume(volume); }
@LuaFunction public final boolean audioSetLoopingAt(IComputerAccess computer, int index, boolean loop) throws LuaException { return byIndexFor(computer, index).audioSetLooping(loop); }
@LuaFunction public final void audioStopAt(IComputerAccess computer, int index) throws LuaException { byIndexFor(computer, index).audioStop(); }

    
    private byte[] audioTableToPcmBytes(java.util.Map<?, ?> table, int len, String fnName, int min, int max, int shiftBits) throws LuaException {
        if (len <= 0) throw new LuaException(fnName + ": table is empty");
        if (len > SPEAKER_MAX_AUDIO_TABLE) throw new LuaException(fnName + ": table too large");
        ByteBuffer buf = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 1; i <= len; i++) {
            Object val = table.get((long) i);
            if (val == null) val = table.get((double) i);
            if (!(val instanceof Number n)) throw new LuaException(fnName + ": table[" + i + "] is not a number");
            double d = n.doubleValue();
            if (!Double.isFinite(d)) throw new LuaException(fnName + ": table[" + i + "] must be finite");
            int sample = (int) d;
            if (sample < min || sample > max) throw new LuaException(fnName + ": table[" + i + "] out of range");
            short pcm = shiftBits == 8 ? (short) (sample << 8) : (short) sample;
            buf.putShort(pcm);
        }
        buf.flip();
        return buf.array();
    }

    private boolean startStream(String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format, String method) throws LuaException {
        return startStreamAtTick(url, volume, format, method, 0L, null, 0);
    }

    private boolean startStreamAtTick(String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format, String method, long startTick) throws LuaException {
        return startStreamAtTick(url, volume, format, method, startTick, null, 0);
    }

    private boolean startStreamAtTick(String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format, String method, long startTick, java.util.UUID syncGroupId, int syncGroupSize) throws LuaException {
        long expectedLifecycle = lifecycleEpochSnapshot();
        validateStreamUrl(url, method);
        return startValidatedStreamAtTick(
            url, volume, format, method, startTick, syncGroupId, syncGroupSize, expectedLifecycle);
    }

    synchronized long lifecycleEpochSnapshot() {
        return lifecycleEpoch;
    }

    synchronized boolean lifecycleEpochMatches(long expected) {
        return lifecycleEpoch == expected;
    }

    boolean startValidatedStream(String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format,
                                 String method, long expectedLifecycle) throws LuaException {
        return startValidatedStreamAtTick(url, volume, format, method, 0L, null, 0, expectedLifecycle);
    }

    private synchronized boolean startValidatedStreamAtTick(
            String url, Optional<Double> volume, HQSpeakerAudioPacket.AudioFormat format, String method,
            long startTick, java.util.UUID syncGroupId, int syncGroupSize, long expectedLifecycle) throws LuaException {
        if (lifecycleEpoch != expectedLifecycle) return false;
        float vol = clampVolChecked(volume.orElse((double) speakerDefaultVolume), "volume");
        speakStop();
        clearIcyMeta();

        if (startTick < 0L) startTick = 0L;
        if (syncGroupId == null) syncGroupSize = 0;
        else syncGroupSize = Math.max(1, Math.min(SPEAKER_MAX_SYNC_GROUP, syncGroupSize));

        if (world instanceof ServerLevel sl) {
            float[] wp = computeWorldPos(method);
            var pkt = new HQSpeakerAudioPacket(speakerSource, format, vol, wp[0], wp[1], wp[2], pos.getX(), pos.getY(), pos.getZ(), url, startTick, syncGroupId, syncGroupSize);
            sendToNearby(sl, pkt, wp[0], wp[1], wp[2]);
        }

        streamActive.set(true);
        streamUrl = url;
        IcyMetaPacket.SPEAKER_REGISTRY.put(speakerSource, this);
        HQSpeakerMod.log("HQSpeaker: started stream (" + method + ") from " + url);
        return true;
    }

    private boolean speakAudioFile(IArguments args, HQSpeakerAudioPacket.AudioFormat fmt, String fnName) throws LuaException {
        ByteBuffer dataBuf = args.getBytes(0);
        byte[] data = new byte[dataBuf.remaining()];
        dataBuf.duplicate().get(data);
        float volume = clampVolChecked(args.optDouble(1, speakerDefaultVolume), "volume");

        if (data.length == 0) throw new LuaException(fnName + ": data is empty");
        if (data.length > SPEAKER_MAX_AUDIO) throw new LuaException(fnName + ": file too large (max " + (SPEAKER_MAX_AUDIO / 1024 / 1024) + " MB)");

        return enqueue(fmt, data, volume);
    }

    private boolean enqueue(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume) {
        return enqueue(fmt, data, volume, 0L, null, 0);
    }

    private boolean enqueue(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume, long startTick) {
        return enqueue(fmt, data, volume, startTick, null, 0);
    }

    private boolean enqueue(HQSpeakerAudioPacket.AudioFormat fmt, byte[] data, float volume, long startTick, java.util.UUID syncGroupId, int syncGroupSize) {
        if (fmt == null) return false;
        if (data == null || data.length == 0) return false;
        if (data.length > SPEAKER_MAX_AUDIO) return false;
        if (!Float.isFinite(volume)) volume = 1.0f;
        volume = Math.max(0.0f, Math.min(3.0f, volume));
        if (startTick < SPEAKER_MIN_START_DELAY) startTick = 0L;
        if (syncGroupId == null) syncGroupSize = 0;
        else syncGroupSize = Math.max(1, Math.min(SPEAKER_MAX_SYNC_GROUP, syncGroupSize));
        if (speakerQueue.size() >= SPEAKER_MAX_QUEUE) return false;
        if (isFiniteFormat(fmt)) {
            synchronized (playerLock) {
                if (finiteTracks.size() >= SPEAKER_MAX_QUEUE) return false;
            }
        }
        
        byte[] safe = java.util.Arrays.copyOf(data, data.length);
        long generation = 0L;
        FiniteServerTrack finite = null;
        if (isFiniteFormat(fmt)) {
            synchronized (playerLock) {
                generation = ++generationCounter;
                finite = new FiniteServerTrack(generation, fmt, volume, looping);
            }
        }
        boolean offered = speakerQueue.offer(new SpeakerChunk(fmt, safe, volume,
            startTick, syncGroupId, syncGroupSize, generation));
        if (offered && finite != null) {
            synchronized (playerLock) {
                if (finiteTracks.size() == 1
                        && finiteTracks.peekFirst().state == PlayerState.ERROR) {
                    finiteTracks.clear();
                }
                finiteTracks.addLast(finite);
                terminalTrack = null;
            }
        }
        if (offered && speakerQueue.size() < SPEAKER_MAX_QUEUE) speakerReadyPending.set(true);
        return offered;
    }

    private static boolean isFiniteFormat(HQSpeakerAudioPacket.AudioFormat format) {
        return format == HQSpeakerAudioPacket.AudioFormat.OGG_VORBIS
            || format == HQSpeakerAudioPacket.AudioFormat.MP3
            || format == HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE;
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

    private void sendControl(FiniteServerTrack track, HQSpeakerControlPacket.Action action,
                             double value) {
        if (!(world instanceof ServerLevel level) || track == null) return;
        float[] worldPos = computeWorldPos("playerControl");
        sendToNearby(level, new HQSpeakerControlPacket(speakerSource,
            track.generation, action, value), worldPos[0], worldPos[1], worldPos[2]);
    }

    private void broadcastStopPacket() {
        if (!(world instanceof ServerLevel sl)) return;
        var pkt = new HQSpeakerStopPacket(speakerSource);
        float[] wp = computeWorldPos("broadcastStop");
        sendToNearby(sl, pkt, wp[0], wp[1], wp[2]);
    }

    static void validateStreamUrl(String url, String method) throws LuaException {
        if (url == null || url.isBlank()) throw new LuaException(method + ": URL cannot be empty");
        if (url.length() > 512) throw new LuaException(method + ": URL too long (max 512 chars)");

        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x20 || c == 0x7F) throw new LuaException(method + ": URL contains illegal character at index " + i);
        }

        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                throw new LuaException(method + ": URL must use http:// or https://");
            if (uri.getUserInfo() != null) throw new LuaException(method + ": URL userinfo is not allowed");
            String host = uri.getHost();
            if (host == null || host.isBlank()) throw new LuaException(method + ": URL has no host");
            if (host.equalsIgnoreCase("localhost") || host.endsWith(".local"))
                throw new LuaException(method + ": URL targets a local host");

            int port = uri.getPort();
            if (port != -1 && port != 80 && port != 443 && port != 8000 && port != 8080 && port != 8443)
                throw new LuaException(method + ": URL uses a blocked port (" + port + ")");

            for (java.net.InetAddress addr : java.net.InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() ||
                    addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                    throw new LuaException(method + ": URL resolves to a private/reserved address");
                }
            }
        } catch (IllegalArgumentException e) {
            throw new LuaException(method + ": malformed URL");
        } catch (java.net.UnknownHostException e) {
            throw new LuaException(method + ": cannot resolve host");
        }
    }
}

