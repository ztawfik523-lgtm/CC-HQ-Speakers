package com.tom.hqspeaker.media;

import com.tom.hqspeaker.config.HQSpeakerServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** One reusable encoded-media store plus bounded range-IO service per running Minecraft server. */
public final class ServerMediaAssets {
    private static final Map<MinecraftServer, ServerMediaAssets> SERVERS = new IdentityHashMap<>();

    private final MediaAssetStore store;
    private final FiniteRangeReadService rangeReads;

    private ServerMediaAssets(MinecraftServer server) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT)
            .resolve("hqspeaker")
            .resolve("media-assets");
        store = new MediaAssetStore(
            root,
            HQSpeakerServerConfig.maxAssetBytes(),
            HQSpeakerServerConfig.maxTotalBytes()
        );
        rangeReads = new FiniteRangeReadService(store);
    }

    public static synchronized ServerMediaAssets get(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server");
        ServerMediaAssets current = SERVERS.get(server);
        if (current != null) return current;

        ServerMediaAssets created = new ServerMediaAssets(server);
        SERVERS.put(server, created);
        return created;
    }

    /**
     * Stop/drain finite range IO before the asset store is allowed to remove files or release its root lock.
     * A failed close remains reachable for retry.
     */
    public static synchronized void closeServer(MinecraftServer server) throws IOException {
        if (server == null) return;
        ServerMediaAssets assets = SERVERS.get(server);
        if (assets == null) return;

        assets.rangeReads.close();
        assets.store.close();
        SERVERS.remove(server, assets);
    }

    public MediaAssetStore store() {
        return store;
    }

    public FiniteRangeReadService rangeReads() {
        return rangeReads;
    }

    public long maxAssetBytes() {
        return store.maxAssetBytes();
    }

    public long maxTotalBytes() {
        return store.maxTotalBytes();
    }
}
