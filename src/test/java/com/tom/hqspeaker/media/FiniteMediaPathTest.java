package com.tom.hqspeaker.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FiniteMediaPathTest {
    @Test
    void normalizesSimpleRelativePaths() {
        assertEquals("folder/song.mp3", FiniteMediaPath.normalize("/folder//./song.mp3"));
        assertEquals("folder/song.ogg", FiniteMediaPath.normalize("folder\\song.ogg"));
    }

    @Test
    void rejectsTraversalAndDriveSyntax() {
        assertThrows(IllegalArgumentException.class, () -> FiniteMediaPath.normalize("../secret"));
        assertThrows(IllegalArgumentException.class, () -> FiniteMediaPath.normalize("folder/../secret"));
        assertThrows(IllegalArgumentException.class, () -> FiniteMediaPath.normalize("C:/secret"));
    }

    @Test
    void rejectsEmptyPath() {
        assertThrows(IllegalArgumentException.class, () -> FiniteMediaPath.normalize("/./"));
    }
}
