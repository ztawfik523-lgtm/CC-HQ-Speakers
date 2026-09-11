package com.tom.hqspeaker.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned safety limits for disk space allocated by HQ Speaker itself. */
public final class HQSpeakerServerConfig {
    private static final long MIB = 1024L * 1024L;
    private static final long MAX_MIB = Long.MAX_VALUE / MIB;

    public static final long DEFAULT_MAX_ASSET_MIB = 512L;
    public static final long DEFAULT_MAX_TOTAL_MIB = 2048L;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.LongValue MAX_ASSET_MIB;
    private static final ModConfigSpec.LongValue MAX_TOTAL_MIB;

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
        SPEC = builder.build();
    }

    private HQSpeakerServerConfig() {}

    public static long maxAssetBytes() {
        return toBytes(MAX_ASSET_MIB.get());
    }

    public static long maxTotalBytes() {
        return toBytes(MAX_TOTAL_MIB.get());
    }

    private static long toBytes(long mib) {
        return mib == 0L ? Long.MAX_VALUE : mib * MIB;
    }
}
