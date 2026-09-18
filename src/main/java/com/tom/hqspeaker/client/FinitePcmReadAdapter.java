package com.tom.hqspeaker.client;

/**
 * Pure renderer-read policy shared by the Minecraft AudioStream adapter and deterministic tests.
 */
final class FinitePcmReadAdapter {
    enum State { DATA, SILENCE, EOF, CANCELLED }

    record Result(State state, byte[] data) {
        Result {
            if (state == null) throw new NullPointerException("state");
            data = data == null ? new byte[0] : data;
            if ((state == State.EOF || state == State.CANCELLED) && data.length != 0) {
                throw new IllegalArgumentException("terminal renderer read cannot carry PCM");
            }
        }
    }

    private FinitePcmReadAdapter() {}

    static Result read(FinitePcmQueue queue, int wantedBytes, int silenceBytes) {
        if (queue == null) throw new NullPointerException("queue");
        if (wantedBytes < 2 || (wantedBytes & 1) != 0) {
            throw new IllegalArgumentException("wantedBytes must contain whole S16 samples");
        }
        if (silenceBytes < 2 || (silenceBytes & 1) != 0) {
            throw new IllegalArgumentException("silenceBytes must contain whole S16 samples");
        }

        FinitePcmQueue.ReadResult result = queue.read(wantedBytes);
        return switch (result.state()) {
            case DATA -> new Result(State.DATA, result.data());
            case STARVED -> new Result(State.SILENCE, new byte[Math.min(wantedBytes, silenceBytes)]);
            case EOF -> new Result(State.EOF, new byte[0]);
            case CANCELLED -> new Result(State.CANCELLED, new byte[0]);
        };
    }
}
