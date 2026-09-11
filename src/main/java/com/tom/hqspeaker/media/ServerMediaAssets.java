package com.tom.hqspeaker.media;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One reusable encoded-media store per running Minecraft server.
 *
 * <p>The limits here are interim M1C safety defaults, not a permanent user-facing storage-policy contract. They are
 * centralised here so a later server configuration can replace them without changing the asset/store primitives.</p>
 */
public final class ServerMediaAssets {
    private static final long MIB = 1024L * 1024L;

    /** Matches the already-established large local-file ceiling from the staged-file prototype. */
    public static final long DEFAULT_MAX_ASSET_BYTES = 512L * MIB;
    /** Interim value only; the final total-store policy remains an explicit project choice. */
    public static final long DEFAULT_MAX_TOTAL_BYTES = 2048L * MIB;

    private static final Map<MinecraftServer, ServerMediaAssets> SERVERS = new IdentityHashMap<>();

    private final MediaAssetStore store;

    private ServerMediaAssets(MinecraftServer server) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT)
            .resolve("hqspeaker")
            .resolve("media-assets");
        store = new MediaAssetStore(root, DEFAULT_MAX_ASSET_BYTES, DEFAULT_MAX_TOTAL_BYTES);
    }

    public static synchronized ServerMediaAssets get(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server");
        ServerMediaAssets current = SERVERS.get(server);
        if (current != null) return current;

        ServerMediaAssets created = new ServerMediaAssets(server);
        SERVERS.put(server, created);
        return created;
    }

    /** Close and forget the asset store for one stopped server. A failed close remains reachable for retry. */
    public static synchronized void closeServer(MinecraftServer server) throws IOException {
        if (server == null) return;
        ServerMediaAssets assets = SERVERS.get(server);
        if (assets == null) return;

        assets.store.close();
        SERVERS.remove(server, assets);
    }

    public MediaAssetStore store() {
        return store;
    }

    public long maxAssetBytes() {
        return store.maxAssetBytes();
    }

    public long maxTotalBytes() {
        return store.maxTotalBytes();
    }
}
