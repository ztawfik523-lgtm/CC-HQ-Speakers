package com.tom.hqspeaker.media;

/** Finite encoded formats which the server can currently inspect and the client has a real decode path for. */
public enum FiniteMediaFormat {
    MP3("mp3"),
    OGG_VORBIS("ogg"),
    WAV("wav"),
    AIFF("aiff"),
    AU("au");

    private final String id;

    FiniteMediaFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
