package com.tom.hqspeaker.media;

/** Finite encoded formats supported by the current server and client paths. */
public enum FiniteMediaFormat {
    MP3("mp3"),
    WAV("wav");

    private final String id;

    FiniteMediaFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
