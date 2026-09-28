package com.tom.hqspeaker.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned storage limits and HQ audio tuning. */
public final class HQSpeakerServerConfig {
    private static final long MIB = 1024L * 1024L;
    private static final long MAX_MIB = Long.MAX_VALUE / MIB;

    public static final long DEFAULT_MAX_ASSET_MIB = 512L;
    public static final long DEFAULT_MAX_TOTAL_MIB = 2048L;

    public static final double DEFAULT_VOLUME = 1.5;
    public static final double DEFAULT_MAX_VOLUME = 3.0;
    public static final double DEFAULT_MAX_RANGE = 256.0;

    private static final double[] DEFAULT_GAIN_ANCHORS = {
        0.0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0
    };
    private static final double[] DEFAULT_RANGE_ANCHORS = {
        0.0, 12.0, 29.0, 48.0, 70.0, 96.0, 132.0
    };

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.LongValue MAX_ASSET_MIB;
    private static final ModConfigSpec.LongValue MAX_TOTAL_MIB;

    private static final ModConfigSpec.DoubleValue AUDIO_DEFAULT_VOLUME;
    private static final ModConfigSpec.DoubleValue AUDIO_MAX_VOLUME;
    private static final ModConfigSpec.BooleanValue AUDIO_ALLOW_RANGE_OVERRIDE;
    private static final ModConfigSpec.DoubleValue AUDIO_MAX_RANGE;
    private static final ModConfigSpec.DoubleValue[] AUDIO_GAIN_ANCHORS;
    private static final ModConfigSpec.DoubleValue[] AUDIO_RANGE_ANCHORS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("mediaStorage");

        MAX_ASSET_MIB = builder
            .comment(
                "Maximum encoded size, in MiB, of one media asset copied into HQ Speaker's server-side store.",
                "This does not limit the ComputerCraft computer's own filesystem.",
                "Set to 0 to disable HQ Speaker's per-asset size limit.",
                "Changing this setting requires a world/server restart."
            )
            .worldRestart()
            .defineInRange("maxAssetMiB", DEFAULT_MAX_ASSET_MIB, 0L, MAX_MIB);

        MAX_TOTAL_MIB = builder
            .comment(
                "Maximum total encoded bytes, in MiB, held by HQ Speaker's server-side prepared-media store.",
                "Unused assets are normally deleted when their final reference is released.",
                "Set to 0 to disable HQ Speaker's total-store size limit.",
                "Changing this setting requires a world/server restart."
            )
            .worldRestart()
            .defineInRange("maxTotalMiB", DEFAULT_MAX_TOTAL_MIB, 0L, MAX_MIB);

        builder.pop();

        builder.push("audio");

        AUDIO_DEFAULT_VOLUME = builder
            .comment(
                "Default logical HQ volume when Lua omits volume.",
                "1.5 is the normal reference point. Reloading this config affects new playback only."
            )
            .defineInRange("defaultVolume", DEFAULT_VOLUME, 0.0, 3.0);

        AUDIO_MAX_VOLUME = builder
            .comment(
                "Maximum logical HQ volume Lua may request.",
                "Requests above this value fail with a Lua error instead of being clamped."
            )
            .defineInRange("maxVolume", DEFAULT_MAX_VOLUME, 0.0, 3.0);

        AUDIO_ALLOW_RANGE_OVERRIDE = builder
            .comment(
                "Whether Lua may explicitly override the automatic volume-derived range."
            )
            .define("allowRangeOverride", true);

        AUDIO_MAX_RANGE = builder
            .comment(
                "Maximum audible range, in blocks, allowed by the server.",
                "Explicit Lua range requests above this value fail with a Lua error.",
                "Automatic range anchors must also stay at or below this value."
            )
            .defineInRange("maxRange", DEFAULT_MAX_RANGE, 1.0, 4096.0);

        builder.push("gain");
        AUDIO_GAIN_ANCHORS = defineAnchorArray(
            builder,
            DEFAULT_GAIN_ANCHORS,
            0.0,
            1.0,
            "Source-gain output at logical volume %s. Values between anchors are linearly interpolated."
        );
        builder.pop();

        builder.push("range");
        AUDIO_RANGE_ANCHORS = defineAnchorArray(
            builder,
            DEFAULT_RANGE_ANCHORS,
            0.0,
            4096.0,
            "Automatic range in blocks at logical volume %s. Values between anchors are linearly interpolated."
        );
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    private HQSpeakerServerConfig() {}

    public static long maxAssetBytes() {
        return toBytes(MAX_ASSET_MIB.get());
    }

    public static long maxTotalBytes() {
        return toBytes(MAX_TOTAL_MIB.get());
    }

    /**
     * Build a new immutable tuning snapshot from the currently loaded SERVER config.
     * Active sources retain the snapshot they already own; later starts call this again.
     */
    public static HQAudioTuningProfile audioProfile() {
        double[] gains = readAnchors(AUDIO_GAIN_ANCHORS);
        double[] ranges = readAnchors(AUDIO_RANGE_ANCHORS);
        return new HQAudioTuningProfile(
            AUDIO_DEFAULT_VOLUME.get(),
            AUDIO_MAX_VOLUME.get(),
            AUDIO_ALLOW_RANGE_OVERRIDE.get(),
            AUDIO_MAX_RANGE.get(),
            gains,
            ranges
        );
    }

    private static ModConfigSpec.DoubleValue[] defineAnchorArray(
            ModConfigSpec.Builder builder,
            double[] defaults,
            double min,
            double max,
            String commentFormat) {
        ModConfigSpec.DoubleValue[] values = new ModConfigSpec.DoubleValue[HQAudioTuningProfile.INPUT_ANCHORS.length];
        for (int i = 0; i < values.length; i++) {
            double input = HQAudioTuningProfile.INPUT_ANCHORS[i];
            values[i] = builder
                .comment(String.format(java.util.Locale.ROOT, commentFormat, input))
                .defineInRange(anchorKey(input), defaults[i], min, max);
        }
        return values;
    }

    private static double[] readAnchors(ModConfigSpec.DoubleValue[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].get();
        return out;
    }

    private static String anchorKey(double input) {
        if (input == Math.rint(input)) return "at" + (int) input;
        return "at" + Double.toString(input).replace('.', '_');
    }

    private static long toBytes(long mib) {
        return mib == 0L ? Long.MAX_VALUE : mib * MIB;
    }
}
