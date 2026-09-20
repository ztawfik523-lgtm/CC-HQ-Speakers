package com.tom.hqspeaker.client.audio;

import com.tom.hqspeaker.HQSpeakerMod;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public final class SharedStreamingGroup {
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private SharedStreamingGroup() {}

    public static Tap open(UUID groupId, String url, float volume, UUID metadataSourceId) {
        Session session = SESSIONS.compute(groupId, (id, existing) -> {
            if (existing != null && existing.matches(url)) return existing;
            if (existing != null) existing.forceClose();
            return new Session(groupId, url, volume, metadataSourceId);
        });
        return session.addTap();
    }

    private static void remove(UUID groupId, Session session) {
        SESSIONS.remove(groupId, session);
    }

    public static final class Tap {
        private final Session session;
        private final int tapId;
        private final BlockingQueue<byte[]> pcmQueue;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private ByteBuffer carry = null;

        private Tap(Session session, int tapId, BlockingQueue<byte[]> pcmQueue) {
            this.session = session;
            this.tapId = tapId;
            this.pcmQueue = pcmQueue;
        }

        public AudioFormat getFormat() { return session.getFormat(); }
        public boolean isRunning() { return session.isRunning(); }
        public void start() { session.startDecode(); }
        public boolean hasData() { return !pcmQueue.isEmpty(); }
        public int getQueueSize() { return pcmQueue.size(); }

        public ByteBuffer readPCM(int maxBytes) {
            if (carry != null && carry.hasRemaining()) {
                int len = Math.min(carry.remaining(), maxBytes);
                len -= len % 2;
                if (len <= 0) return ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
                int oldLimit = carry.limit();
                carry.limit(carry.position() + len);
                ByteBuffer out = ByteBuffer.allocateDirect(len);
                out.put(carry);
                out.flip();
                carry.limit(oldLimit);
                if (!carry.hasRemaining()) carry = null;
                return out;
            }
            if (!session.isRunning() && pcmQueue.isEmpty()) return null;
            byte[] data = pcmQueue.poll();
            if (data == null) return ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
            int len = Math.min(data.length, maxBytes);
            len -= len % 2;
            if (len <= 0) return ByteBuffer.allocateDirect(0).asReadOnlyBuffer();
            ByteBuffer src = ByteBuffer.allocateDirect(data.length);
            src.put(data).flip();
            if (src.remaining() <= len) return src;
            ByteBuffer out = ByteBuffer.allocateDirect(len);
            int oldLimit = src.limit();
            src.limit(src.position() + len);
            out.put(src);
            out.flip();
            src.limit(oldLimit);
            carry = src.slice();
            return out;
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                session.removeTap(tapId);
                pcmQueue.clear();
            }
        }
    }

    private static final class Session {
        private static final int DISTRIBUTION_CHUNK_BYTES = 9600; 
        private final UUID groupId;
        private final String url;
        private final float volume;
        private final UUID metadataSourceId;
        private final StreamingAudioSource source;
        private final ConcurrentHashMap<Integer, BlockingQueue<byte[]>> taps = new ConcurrentHashMap<>();
        private final AtomicInteger nextTapId = new AtomicInteger(1);
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean decodeStarted = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private Thread distributorThread;

        private Session(UUID groupId, String url, float volume, UUID metadataSourceId) {
            this.groupId = groupId;
            this.url = url;
            this.volume = volume;
            this.metadataSourceId = metadataSourceId;
            this.source = new StreamingAudioSource(url, volume);
            this.source.setMetadataListener((rawTitle, station, genre, desc) -> {
                try {
                    com.tom.hqspeaker.network.HQSpeakerNetwork.sendToServer(
                        new com.tom.hqspeaker.network.IcyMetaPacket(metadataSourceId, rawTitle, station, genre, desc));
                } catch (Exception e) {
                    HQSpeakerMod.warn("SharedStreamingGroup: failed to send ICY meta — " + e.getMessage());
                }
            });
        }

        private boolean matches(String url) {
            return this.url.equals(url);
        }

        private void startDecode() {
            if (closed.get() || !decodeStarted.compareAndSet(false, true)) return;
            if (running.compareAndSet(false, true)) {
                source.start();
                distributorThread = new Thread(this::distributeLoop, "HQSpeaker-SharedStream-" + groupId);
                distributorThread.setDaemon(true);
                distributorThread.start();
                HQSpeakerMod.log("SharedStreamingGroup: sealed and started MP3 radio session " + groupId
                    + " from " + url + " with " + taps.size() + " taps");
            }
        }

        private Tap addTap() {
            if (closed.get() || decodeStarted.get()) return null;
            int id = nextTapId.getAndIncrement();
            BlockingQueue<byte[]> q = new LinkedBlockingQueue<>(400);
            taps.put(id, q);
            return new Tap(this, id, q);
        }

        private void removeTap(int id) {
            taps.remove(id);
            if (taps.isEmpty()) forceClose();
        }

        private AudioFormat getFormat() { return source.getFormat(); }
        private boolean isRunning() {
            return !closed.get() && ((!decodeStarted.get() && !taps.isEmpty())
                || (running.get() && source.isRunning()));
        }

        private void distributeLoop() {
            try {
                while (running.get()) {
                    ByteBuffer pcm = source.readPCM(DISTRIBUTION_CHUNK_BYTES);
                    if (pcm == null) break;
                    if (pcm.remaining() <= 0) {
                        try { Thread.sleep(2L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }
                    byte[] data = new byte[pcm.remaining()];
                    pcm.get(data);
                    if (data.length == 0) continue;
                    for (BlockingQueue<byte[]> q : taps.values()) {
                        byte[] copy = Arrays.copyOf(data, data.length);
                        while (!q.offer(copy) && running.get()) {
                            q.poll();
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) HQSpeakerMod.warn("SharedStreamingGroup: distributor error — " + e.getMessage());
            } finally {
                forceClose();
            }
        }

        private void forceClose() {
            if (!closed.compareAndSet(false, true)) return;
            running.set(false);
            if (distributorThread != null) distributorThread.interrupt();
            source.stop();
            taps.values().forEach(BlockingQueue::clear);
            taps.clear();
            remove(groupId, this);
            HQSpeakerMod.log("SharedStreamingGroup: closed session " + groupId);
        }
    }
}
