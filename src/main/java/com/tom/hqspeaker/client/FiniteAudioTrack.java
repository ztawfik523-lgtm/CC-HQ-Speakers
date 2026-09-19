package com.tom.hqspeaker.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Retained mono signed-16-bit PCM and its frame-aligned logical cursor. */
final class FiniteAudioTrack {
    private static final int FRAME_SIZE = 2;

    private final byte[] pcm;
    private final int sampleRate;
    private int cursorBytes;
    private boolean looping;

    FiniteAudioTrack(byte[] pcm, int sampleRate, boolean looping) {
        if (pcm == null || pcm.length == 0 || (pcm.length % FRAME_SIZE) != 0) {
            throw new IllegalArgumentException("PCM must contain complete 16-bit mono frames");
        }
        if (sampleRate <= 0) throw new IllegalArgumentException("sample rate must be positive");
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.looping = looping;
    }

    synchronized ByteBuffer read(int requestedBytes) {
        int wanted = requestedBytes - Math.floorMod(requestedBytes, FRAME_SIZE);
        if (wanted <= 0) return null;
        if (!looping && cursorBytes >= pcm.length) return null;

        int count = looping ? wanted : Math.min(wanted, pcm.length - cursorBytes);
        ByteBuffer output = ByteBuffer.allocateDirect(count).order(ByteOrder.LITTLE_ENDIAN);
        int remaining = count;
        while (remaining > 0) {
            if (cursorBytes >= pcm.length) cursorBytes = 0;
            int copy = Math.min(remaining, pcm.length - cursorBytes);
            output.put(pcm, cursorBytes, copy);
            cursorBytes += copy;
            remaining -= copy;
        }
        output.flip();
        return output;
    }

    synchronized double seek(double seconds) {
        double clamped = Math.max(0.0, Math.min(durationSeconds(), seconds));
        long frame = Math.round(clamped * sampleRate);
        long maxFrames = pcm.length / FRAME_SIZE;
        frame = Math.max(0L, Math.min(maxFrames, frame));
        cursorBytes = Math.toIntExact(frame * FRAME_SIZE);
        return cursorBytes / (double) FRAME_SIZE / sampleRate;
    }

    synchronized void rewind() { cursorBytes = 0; }
    synchronized FiniteAudioTrack fork(double seconds, boolean looping) {
        FiniteAudioTrack copy = new FiniteAudioTrack(pcm, sampleRate, looping);
        copy.seek(seconds);
        return copy;
    }
    synchronized void setLooping(boolean looping) { this.looping = looping; }
    synchronized boolean isLooping() { return looping; }
    synchronized boolean isAtEnd() { return !looping && cursorBytes >= pcm.length; }
    synchronized double cursorSeconds() { return cursorBytes / (double) FRAME_SIZE / sampleRate; }
    double durationSeconds() { return (pcm.length / (double) FRAME_SIZE) / sampleRate; }
    int sampleRate() { return sampleRate; }
    int frameCount() { return pcm.length / FRAME_SIZE; }
}
