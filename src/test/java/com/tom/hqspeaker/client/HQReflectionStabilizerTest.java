package com.tom.hqspeaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HQReflectionStabilizerTest {
    @Test
    void lowOcclusionStaysAnchoredToRealSpeaker() {
        HQReflectionStabilizer stabilizer = new HQReflectionStabilizer();
        var physical = new HQReflectionStabilizer.Point(0, 0, 0);
        var reflected = new HQReflectionStabilizer.Point(2, 0, 0);

        assertEquals(physical, stabilizer.update(physical, reflected, 0.2));
    }

    @Test
    void highOcclusionUsesBoundedBlendedReflectionAndSmoothRedirect() {
        HQReflectionStabilizer stabilizer = new HQReflectionStabilizer();
        var physical = new HQReflectionStabilizer.Point(0, 0, 0);
        var reflected = new HQReflectionStabilizer.Point(10, 0, 0);

        var first = stabilizer.update(physical, reflected, 0.9);

        // Reflected displacement is capped at 2.5, blended to .875, then approached by .22.
        assertEquals(0.1925, first.x(), 1.0e-9);
        assertEquals(0.0, first.y(), 1.0e-9);
        assertEquals(0.0, first.z(), 1.0e-9);
    }

    @Test
    void reflectionHemisphereFlipMovesTowardRealSpeakerFirst() {
        HQReflectionStabilizer stabilizer = new HQReflectionStabilizer();
        var physical = new HQReflectionStabilizer.Point(0, 0, 0);

        // Build a material positive redirect.
        for (int i = 0; i < 8; i++) {
            stabilizer.update(physical, new HQReflectionStabilizer.Point(2, 0, 0), 0.9);
        }
        double before = stabilizer.currentOrPhysical(physical).x();
        assertTrue(before > HQReflectionStabilizer.MATERIAL_OFFSET);

        var flipped = stabilizer.update(physical, new HQReflectionStabilizer.Point(-2, 0, 0), 0.9);
        assertTrue(flipped.x() > 0.0, "must not teleport directly across the real source");
        assertTrue(flipped.x() < before, "flip stage should move toward the real source");
    }

    @Test
    void movingPhysicalSourceCarriesExistingAcousticOffset() {
        HQReflectionStabilizer stabilizer = new HQReflectionStabilizer();
        var p0 = new HQReflectionStabilizer.Point(0, 0, 0);
        stabilizer.update(p0, new HQReflectionStabilizer.Point(2, 0, 0), 0.9);

        var p1 = new HQReflectionStabilizer.Point(5, 0, 0);
        var moved = stabilizer.update(p1, new HQReflectionStabilizer.Point(7, 0, 0), 0.9);

        assertTrue(moved.x() > 5.0, "redirect offset should move with the physical source");
    }
}
