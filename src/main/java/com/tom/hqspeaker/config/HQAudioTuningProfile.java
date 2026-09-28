package com.tom.hqspeaker.config;

import java.util.Arrays;

/**
 * Immutable server-side audio tuning snapshot.
 *
 * <p>A snapshot belongs to one HQ continuous source lifetime. Reloading the NeoForge
 * server config affects later sources only; an already-running source keeps the profile
 * it started with.</p>
 */
public final class HQAudioTuningProfile {
    public static final double[] INPUT_ANCHORS = { 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0 };

    private final double defaultVolume;
    private final double maxVolume;
    private final boolean allowRangeOverride;
    private final double maxRange;
    private final double[] gainAnchors;
    private final double[] rangeAnchors;

    public record Resolved(float logicalVolume, float gain, float range, boolean explicitRange) {}

    public HQAudioTuningProfile(
            double defaultVolume,
            double maxVolume,
            boolean allowRangeOverride,
            double maxRange,
            double[] gainAnchors,
            double[] rangeAnchors) {
        requireFinite(defaultVolume, "defaultVolume");
        requireFinite(maxVolume, "maxVolume");
        requireFinite(maxRange, "maxRange");

        if (maxVolume < 0.0 || maxVolume > 3.0) {
            throw new IllegalArgumentException("maxVolume must be between 0 and 3");
        }
        if (defaultVolume < 0.0 || defaultVolume > maxVolume) {
            throw new IllegalArgumentException("defaultVolume must be between 0 and maxVolume");
        }
        if (maxRange <= 0.0) {
            throw new IllegalArgumentException("maxRange must be greater than 0");
        }

        this.gainAnchors = validateAnchors(gainAnchors, "gain", 0.0, 1.0);
        this.rangeAnchors = validateAnchors(rangeAnchors, "range", 0.0, maxRange);
        this.defaultVolume = defaultVolume;
        this.maxVolume = maxVolume;
        this.allowRangeOverride = allowRangeOverride;
        this.maxRange = maxRange;
    }

    public double defaultVolume() {
        return defaultVolume;
    }

    public double maxVolume() {
        return maxVolume;
    }

    public boolean allowRangeOverride() {
        return allowRangeOverride;
    }

    public double maxRange() {
        return maxRange;
    }

    public double[] gainAnchors() {
        return gainAnchors.clone();
    }

    public double[] rangeAnchors() {
        return rangeAnchors.clone();
    }

    public Resolved resolve(double logicalVolume, Double explicitRange) {
        validateVolume(logicalVolume);

        if (explicitRange != null) {
            validateExplicitRange(explicitRange);
            return new Resolved(
                (float) logicalVolume,
                interpolate(gainAnchors, logicalVolume),
                explicitRange.floatValue(),
                true
            );
        }

        return new Resolved(
            (float) logicalVolume,
            interpolate(gainAnchors, logicalVolume),
            interpolate(rangeAnchors, logicalVolume),
            false
        );
    }

    public Resolved resolveDefault(Double explicitRange) {
        return resolve(defaultVolume, explicitRange);
    }

    public float gainFor(double logicalVolume) {
        validateVolume(logicalVolume);
        return interpolate(gainAnchors, logicalVolume);
    }

    public float automaticRangeFor(double logicalVolume) {
        validateVolume(logicalVolume);
        return interpolate(rangeAnchors, logicalVolume);
    }

    public void validateVolume(double volume) {
        requireFinite(volume, "volume");
        if (volume < 0.0 || volume > maxVolume) {
            throw new IllegalArgumentException(
                "volume must be between 0 and " + trimNumber(maxVolume) + " (server maximum)");
        }
    }

    public void validateExplicitRange(double range) {
        requireFinite(range, "range");
        if (!allowRangeOverride) {
            throw new IllegalArgumentException("range override is disabled by the server");
        }
        if (range <= 0.0 || range > maxRange) {
            throw new IllegalArgumentException(
                "range must be greater than 0 and at most " + trimNumber(maxRange) + " blocks (server maximum)");
        }
    }

    private static float interpolate(double[] outputs, double input) {
        if (input <= INPUT_ANCHORS[0]) return (float) outputs[0];
        int last = INPUT_ANCHORS.length - 1;
        if (input >= INPUT_ANCHORS[last]) return (float) outputs[last];

        for (int i = 0; i < last; i++) {
            double low = INPUT_ANCHORS[i];
            double high = INPUT_ANCHORS[i + 1];
            if (input <= high) {
                double t = (input - low) / (high - low);
                return (float) (outputs[i] + (outputs[i + 1] - outputs[i]) * t);
            }
        }
        return (float) outputs[last];
    }

    private static double[] validateAnchors(double[] values, String name, double min, double max) {
        if (values == null || values.length != INPUT_ANCHORS.length) {
            throw new IllegalArgumentException(name + " anchors must contain exactly " + INPUT_ANCHORS.length + " values");
        }

        double[] copy = values.clone();
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < copy.length; i++) {
            double value = copy[i];
            requireFinite(value, name + " anchor " + INPUT_ANCHORS[i]);
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                    name + " anchor " + INPUT_ANCHORS[i] + " must be between " + min + " and " + max);
            }
            if (value < previous) {
                throw new IllegalArgumentException(name + " anchors must be nondecreasing");
            }
            previous = value;
        }
        return copy;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    @Override
    public String toString() {
        return "HQAudioTuningProfile{" +
            "defaultVolume=" + defaultVolume +
            ", maxVolume=" + maxVolume +
            ", allowRangeOverride=" + allowRangeOverride +
            ", maxRange=" + maxRange +
            ", gainAnchors=" + Arrays.toString(gainAnchors) +
            ", rangeAnchors=" + Arrays.toString(rangeAnchors) +
            '}';
    }
}
