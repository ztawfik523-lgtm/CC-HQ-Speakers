package com.tom.hqspeaker.media;

import com.tom.hqspeaker.config.HQSpeakerServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** One reusable encoded-media store plus bounded range-IO/release services per running Minecraft server. */
public final class ServerMediaAssets {
    private static final Map<MinecraftServer, ServerMediaAssets> SERVERS = new IdentityHashMap<>();
    private static final int RELEASE_RETRY_TICKS = 20;

    private final MediaAssetStore store;
    private final MediaAssetReleaseQueue releases;
    private final FiniteRangeReadService rangeReads;
    private int releaseRetryTicker;
    private boolean closing;

    private ServerMediaAssets(MinecraftServer server) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT)
            .resolve("hqspeaker")
            .resolve("media-assets");
        store = new MediaAssetStore(
            root,
            HQSpeakerServerConfig.maxAssetBytes(),
            HQSpeakerServerConfig.maxTotalBytes()
        );
        releases = new MediaAssetReleaseQueue(store);
        rangeReads = new FiniteRangeReadService(store, releases);
    }

    public static synchronized ServerMediaAssets get(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server");
        retryClosingServers();
        ServerMediaAssets current = SERVERS.get(server);
        if (current != null) {
            if (current.closing) throw new IOException("server media assets are shutting down");
            return current;
        }

        ServerMediaAssets created = new ServerMediaAssets(server);
        SERVERS.put(server, created);
        return created;
    }

    /** Retry deferred logical releases at a modest cadence rather than hammering a persistently failing filesystem. */
    public static synchronized void tickPendingReleases() {
        for (ServerMediaAssets assets : SERVERS.values()) {
            if (assets.closing) continue;
            if (++assets.releaseRetryTicker < RELEASE_RETRY_TICKS) continue;
            assets.releaseRetryTicker = 0;
            assets.releases.retryPending();
        }
    }

    /**
     * Begin range shutdown early in the server-stop lifecycle. This is intentionally non-blocking so normal server
     * cleanup can continue before the final close waits for workers.
     */
    public static synchronized void beginCloseServer(MinecraftServer server) {
        if (server == null) return;
        ServerMediaAssets assets = SERVERS.get(server);
        if (assets == null) return;
        assets.closing = true;
        assets.rangeReads.beginClose();
    }

    /**
     * Stop/drain finite range IO before the asset store removes files or releases its root lock. Any failure leaves
     * the stopped-server entry reachable. A later close attempt or a new-server get() retries it.
     */
    public static synchronized void closeServer(MinecraftServer server) throws IOException {
        if (server == null) return;
        ServerMediaAssets assets = SERVERS.get(server);
        if (assets == null) return;

        assets.closing = true;
        assets.finishClose();
        SERVERS.remove(server, assets);
    }

    private void finishClose() throws IOException {
        rangeReads.close();
        releases.retryPending();
        store.close();
    }

    private static void retryClosingServers() {
        var iterator = SERVERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerMediaAssets assets = entry.getValue();
            if (!assets.closing) continue;
            try {
                assets.finishClose();
                iterator.remove();
            } catch (IOException ignored) {
                // Keep ownership and the root lock until it is genuinely safe to close.
            }
        }
    }

    public MediaAssetStore store() {
        return store;
    }

    public MediaAssetReleaseQueue releases() {
        return releases;
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
