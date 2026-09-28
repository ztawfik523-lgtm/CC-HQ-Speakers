package com.tom.hqspeaker.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HQAudioTuningProfileTest {
    private static HQAudioTuningProfile defaults() {
        return new HQAudioTuningProfile(
            1.5,
            3.0,
            true,
            256.0,
            new double[]{ 0.0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0 },
            new double[]{ 0.0, 12.0, 29.0, 48.0, 70.0, 96.0, 132.0 }
        );
    }

    @Test
    void exactAnchorsStayExact() {
        HQAudioTuningProfile profile = defaults();

        assertEquals(0.50f, profile.gainFor(1.5), 1.0e-6f);
        assertEquals(48.0f, profile.automaticRangeFor(1.5), 1.0e-6f);
        assertEquals(1.0f, profile.gainFor(3.0), 1.0e-6f);
        assertEquals(132.0f, profile.automaticRangeFor(3.0), 1.0e-6f);
    }

    @Test
    void valuesBetweenAnchorsInterpolateLinearly() {
        HQAudioTuningProfile profile = defaults();

        assertEquals(0.585f, profile.gainFor(1.75), 1.0e-6f);
        assertEquals(59.0f, profile.automaticRangeFor(1.75), 1.0e-6f);
        assertEquals(58.12f, profile.automaticRangeFor(1.73), 1.0e-4f);
    }

    @Test
    void explicitRangeOverridesOnlyRange() {
        HQAudioTuningProfile.Resolved resolved = defaults().resolve(2.0, 35.0);

        assertEquals(2.0f, resolved.logicalVolume(), 1.0e-6f);
        assertEquals(0.67f, resolved.gain(), 1.0e-6f);
        assertEquals(35.0f, resolved.range(), 1.0e-6f);
        assertTrue(resolved.explicitRange());
    }

    @Test
    void automaticRangeTracksVolume() {
        HQAudioTuningProfile.Resolved resolved = defaults().resolve(2.0, null);

        assertEquals(0.67f, resolved.gain(), 1.0e-6f);
        assertEquals(70.0f, resolved.range(), 1.0e-6f);
        assertFalse(resolved.explicitRange());
    }

    @Test
    void badRequestsAreErrorsNotClamps() {
        HQAudioTuningProfile profile = defaults();

        assertThrows(IllegalArgumentException.class, () -> profile.resolve(-0.01, null));
        assertThrows(IllegalArgumentException.class, () -> profile.resolve(3.01, null));
        assertThrows(IllegalArgumentException.class, () -> profile.resolve(Double.NaN, null));
        assertThrows(IllegalArgumentException.class, () -> profile.resolve(1.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> profile.resolve(1.5, 256.01));
        assertThrows(IllegalArgumentException.class, () -> profile.resolve(1.5, Double.POSITIVE_INFINITY));
    }

    @Test
    void disabledRangeOverrideIsRejected() {
        HQAudioTuningProfile profile = new HQAudioTuningProfile(
            1.5,
            3.0,
            false,
            256.0,
            new double[]{ 0.0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0 },
            new double[]{ 0.0, 12.0, 29.0, 48.0, 70.0, 96.0, 132.0 }
        );

        assertThrows(IllegalArgumentException.class, () -> profile.resolve(1.5, 48.0));
    }

    @Test
    void malformedConfigProfileIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HQAudioTuningProfile(
            1.5,
            3.0,
            true,
            100.0,
            new double[]{ 0.0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0 },
            new double[]{ 0.0, 12.0, 29.0, 48.0, 70.0, 96.0, 132.0 }
        ));

        assertThrows(IllegalArgumentException.class, () -> new HQAudioTuningProfile(
            1.5,
            3.0,
            true,
            256.0,
            new double[]{ 0.0, 0.17, 0.34, 0.50, 0.40, 0.84, 1.0 },
            new double[]{ 0.0, 12.0, 29.0, 48.0, 70.0, 96.0, 132.0 }
        ));
    }
}
