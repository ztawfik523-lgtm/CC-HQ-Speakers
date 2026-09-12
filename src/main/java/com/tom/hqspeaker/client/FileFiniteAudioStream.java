package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.network.HQFiniteMediaBeginPacket;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Incremental finite decoder backed by the encoded client cache file.
 * It intentionally never materialises the whole decoded track in RAM.
 */
public final class FileFiniteAudioStream implements AudioStream {
    private interface Decoder extends AutoCloseable {
        javax.sound.sampled.AudioFormat format();
        double durationSeconds();
        ByteBuffer read(int maxBytes) throws IOException;
        double seek(double seconds) throws IOException;
        void setLooping(boolean looping);
        boolean ended();
        @Override void close();
    }

    private final Decoder decoder;
    private boolean firstReadLogged;

    public FileFiniteAudioStream(Path file, HQFiniteMediaBeginPacket.MediaFormat declaredFormat, boolean looping) throws IOException {
        boolean ogg = declaredFormat == HQFiniteMediaBeginPacket.MediaFormat.OGG
            || (declaredFormat == HQFiniteMediaBeginPacket.MediaFormat.AUDIO_FILE && hasMagic(file, new byte[]{ 'O', 'g', 'g', 'S' }));
        decoder = ogg ? new VorbisDecoder(file, looping) : new JavaSoundDecoder(file, looping);
    }

    public double durationSeconds() { return decoder.durationSeconds(); }
    public double seek(double seconds) throws IOException { return decoder.seek(seconds); }
    public void setLooping(boolean looping) { decoder.setLooping(looping); }
    public boolean ended() { return decoder.ended(); }

    @Override public javax.sound.sampled.AudioFormat getFormat() { return decoder.format(); }

    @Override
    public ByteBuffer read(int maxBytes) throws IOException {
        try {
            ByteBuffer out = decoder.read(maxBytes);
            if (!firstReadLogged) {
                firstReadLogged = true;
                HQSpeakerMod.log("M1E finite decoder first PCM read maxBytes=" + maxBytes
                    + " returned=" + (out == null ? "null" : out.remaining())
                    + " format=" + decoder.format()
                    + " stats=" + pcmStats(out));
            }
            return out;
        } catch (IOException e) {
            HQSpeakerMod.error("M1E finite decoder PCM read failed: " + safeMessage(e));
            throw e;
        } catch (RuntimeException e) {
            HQSpeakerMod.error("M1E finite decoder PCM read runtime failure: " + safeMessage(e));
            throw e;
        }
    }

    @Override public void close() { decoder.close(); }

    private static boolean hasMagic(Path file, byte[] magic) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(magic.length);
            if (head.length != magic.length) return false;
            for (int i = 0; i < magic.length; i++) if (head[i] != magic[i]) return false;
            return true;
        }
    }

    private static String pcmStats(ByteBuffer out) {
        if (out == null) return "null";
        ByteBuffer probe = out.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int availableSamples = probe.remaining() / 2;
        int samples = Math.min(availableSamples, 4096);
        if (samples <= 0) return "empty";

        int min = Short.MAX_VALUE;
        int max = Short.MIN_VALUE;
        int nonZero = 0;
        long sumAbs = 0L;
        for (int i = 0; i < samples; i++) {
            short sample = probe.getShort();
            int value = sample;
            if (value < min) min = value;
            if (value > max) max = value;
            if (value != 0) nonZero++;
            sumAbs += Math.abs((long) value);
        }
        double meanAbs = sumAbs / (double) samples;
        return "samplesChecked=" + samples + "/" + availableSamples
            + ",nonZero=" + nonZero + ",min=" + min + ",max=" + max + ",meanAbs=" + meanAbs;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static final class VorbisDecoder implements Decoder {
        private final Path file;
        private long handle;
        private int channels;
        private int sampleRate;
        private double duration;
        private boolean looping;
        private boolean ended;

        VorbisDecoder(Path file, boolean looping) throws IOException {
            this.file = file;
            this.looping = looping;
            open();
        }

        private void open() throws IOException {
            int[] error = new int[1];
            handle = STBVorbis.stb_vorbis_open_filename(file.toAbsolutePath().toString(), error, null);
            if (handle == MemoryUtil.NULL) throw new IOException("STBVorbis could not open file (error " + error[0] + ")");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                sampleRate = info.sample_rate();
            }
            if (channels <= 0 || channels > 8 || sampleRate <= 0) {
                closeHandle();
                throw new IOException("invalid OGG channel/rate metadata");
            }
            duration = STBVorbis.stb_vorbis_stream_length_in_seconds(handle);
            if (!Double.isFinite(duration) || duration < 0.0) duration = 0.0;
            ended = false;
        }

        @Override public javax.sound.sampled.AudioFormat format() { return monoFormat(sampleRate); }
        @Override public double durationSeconds() { return duration; }
        @Override public void setLooping(boolean looping) { this.looping = looping; }
        @Override public boolean ended() { return ended; }

        @Override
        public synchronized ByteBuffer read(int maxBytes) throws IOException {
            if (handle == MemoryUtil.NULL || ended) return null;
            int framesWanted = Math.max(1, maxBytes / 2);
            ShortBuffer interleaved = MemoryUtil.memAllocShort(framesWanted * channels);
            try {
                int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, interleaved);
                if (frames <= 0 && looping) {
                    if (!STBVorbis.stb_vorbis_seek_start(handle)) throw new IOException("OGG loop rewind failed");
                    frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, interleaved.clear());
                }
                if (frames <= 0) {
                    ended = true;
                    return null;
                }
                ByteBuffer out = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int frame = 0; frame < frames; frame++) {
                    long sum = 0;
                    int base = frame * channels;
                    for (int channel = 0; channel < channels; channel++) sum += interleaved.get(base + channel);
                    out.putShort((short) (sum / channels));
                }
                out.flip();
                return out;
            } finally {
                MemoryUtil.memFree(interleaved);
            }
        }

        @Override
        public synchronized double seek(double seconds) throws IOException {
            if (handle == MemoryUtil.NULL) throw new IOException("OGG decoder is closed");
            double max = duration > 0.0 ? duration : Double.MAX_VALUE;
            double target = Double.isFinite(seconds) ? Math.max(0.0, Math.min(max, seconds)) : 0.0;
            long sample = Math.round(target * sampleRate);
            if (sample > 0xFFFF_FFFFL) throw new IOException("OGG seek target is too large");
            if (!STBVorbis.stb_vorbis_seek(handle, (int) sample)) throw new IOException("OGG seek failed");
            ended = false;
            return target;
        }

        @Override public synchronized void close() { closeHandle(); ended = true; }
        private void closeHandle() {
            if (handle != MemoryUtil.NULL) {
                STBVorbis.stb_vorbis_close(handle);
                handle = MemoryUtil.NULL;
            }
        }
    }

    private static final class JavaSoundDecoder implements Decoder {
        private final Path file;
        private AudioInputStream encoded;
        private AudioInputStream decoded;
        private javax.sound.sampled.AudioFormat format;
        private int channels;
        private int sampleRate;
        private int frameSize;
        private double duration;
        private boolean looping;
        private boolean ended;

        JavaSoundDecoder(Path file, boolean looping) throws IOException {
            this.file = file;
            this.looping = looping;
            duration = readDuration(file);
            open();
        }

        private void open() throws IOException {
            closeStreams();
            try {
                encoded = AudioSystem.getAudioInputStream(file.toFile());
                javax.sound.sampled.AudioFormat source = encoded.getFormat();
                channels = source.getChannels();
                sampleRate = Math.round(source.getSampleRate());
                if (channels <= 0 || channels > 8) throw new IOException("invalid audio channel count " + channels);
                if (sampleRate <= 0) sampleRate = 44_100;
                javax.sound.sampled.AudioFormat decodedFormat = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, channels, channels * 2, sampleRate, false);
                decoded = AudioSystem.getAudioInputStream(decodedFormat, encoded);
                frameSize = channels * 2;
                format = monoFormat(sampleRate);
                ended = false;
            } catch (Exception e) {
                closeStreams();
                if (e instanceof IOException io) throw io;
                throw new IOException("unsupported or invalid audio file", e);
            }
        }

        @Override public javax.sound.sampled.AudioFormat format() { return format; }
        @Override public double durationSeconds() { return duration; }
        @Override public void setLooping(boolean looping) { this.looping = looping; }
        @Override public boolean ended() { return ended; }

        @Override
        public synchronized ByteBuffer read(int maxBytes) throws IOException {
            if (decoded == null || ended) return null;
            int framesWanted = Math.max(1, maxBytes / 2);
            byte[] source = new byte[framesWanted * frameSize];
            int read = decoded.read(source);
            if (read < 0 && looping) {
                open();
                read = decoded.read(source);
            }
            if (read <= 0) {
                ended = true;
                return null;
            }
            int frames = read / frameSize;
            if (frames <= 0) return ByteBuffer.allocateDirect(0);
            ByteBuffer out = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int frame = 0; frame < frames; frame++) {
                long sum = 0;
                int base = frame * frameSize;
                for (int channel = 0; channel < channels; channel++) {
                    int index = base + channel * 2;
                    sum += (short) ((source[index] & 0xFF) | (source[index + 1] << 8));
                }
                out.putShort((short) (sum / channels));
            }
            out.flip();
            return out;
        }

        @Override
        public synchronized double seek(double seconds) throws IOException {
            double max = duration > 0.0 ? duration : Double.MAX_VALUE;
            double target = Double.isFinite(seconds) ? Math.max(0.0, Math.min(max, seconds)) : 0.0;
            open();
            long frames = Math.max(0L, Math.round(target * sampleRate));
            long remaining = frames * (long) frameSize;
            byte[] discard = new byte[8192 - (8192 % frameSize)];
            while (remaining > 0L) {
                long skipped = decoded.skip(remaining);
                if (skipped > 0L) {
                    remaining -= skipped;
                    continue;
                }
                int wanted = (int) Math.min(discard.length, remaining);
                wanted -= wanted % frameSize;
                if (wanted <= 0) break;
                int read = decoded.read(discard, 0, wanted);
                if (read < 0) break;
                remaining -= read;
            }
            ended = false;
            return target;
        }

        @Override public synchronized void close() { closeStreams(); ended = true; }

        private void closeStreams() {
            if (decoded != null) {
                try { decoded.close(); } catch (IOException ignored) {}
                decoded = null;
            }
            if (encoded != null) {
                try { encoded.close(); } catch (IOException ignored) {}
                encoded = null;
            }
        }

        private static double readDuration(Path file) {
            try {
                AudioFileFormat aff = AudioSystem.getAudioFileFormat(file.toFile());
                Map<String, Object> properties = aff.properties();
                Object duration = properties.get("duration");
                if (duration instanceof Number number) {
                    double seconds = number.doubleValue() / 1_000_000.0;
                    if (Double.isFinite(seconds) && seconds > 0.0) return seconds;
                }
                javax.sound.sampled.AudioFormat f = aff.getFormat();
                int frames = aff.getFrameLength();
                float frameRate = f.getFrameRate();
                if (frames > 0 && frameRate > 0.0f) return frames / (double) frameRate;
            } catch (Exception ignored) {}
            return 0.0;
        }
    }

    private static javax.sound.sampled.AudioFormat monoFormat(int sampleRate) {
        return new javax.sound.sampled.AudioFormat(
            javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, false);
    }
}
