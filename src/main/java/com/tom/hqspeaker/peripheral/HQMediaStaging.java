package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.config.HQSpeakerServerConfig;
import com.tom.hqspeaker.media.FiniteMediaPath;
import com.tom.hqspeaker.media.MediaAsset;
import com.tom.hqspeaker.media.MediaAssetReleaseQueue;
import com.tom.hqspeaker.media.MediaAssetStore;
import com.tom.hqspeaker.media.MediaMetadata;
import com.tom.hqspeaker.media.MediaStorageLimits;
import com.tom.hqspeaker.media.ModernFiniteMediaAnalyzer;
import com.tom.hqspeaker.media.ServerMediaAssets;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ComputerCraft-visible writable staging for finite local files.
 *
 * <p>Staging is deliberately separate from playback. A staged file is only a temporary import source; once prepared,
 * its encoded bytes live in the server-wide {@link MediaAssetStore} under an asset UUID.</p>
 */
public final class HQMediaStaging {
    public record StagedFile(String path, long sizeBytes, SeekableByteChannel channel) {}

    private record Binding(IComputerAccess computer, String location) {}

    private final MinecraftServer server;
    private final UUID stagingId = UUID.randomUUID();
    private final WritableMount mount;
    private final long maxStagedBytes;
    private final Map<Integer, Binding> bindings = new ConcurrentHashMap<>();
    private final Set<Integer> attachedComputerIds = ConcurrentHashMap.newKeySet();
    private final Object ownershipLock = new Object();
    private final Map<Integer, Set<UUID>> preparedByComputerId = new HashMap<>();

    public HQMediaStaging(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("media staging requires a server level");
        }
        server = serverLevel.getServer();
        maxStagedBytes = MediaStorageLimits.writableMountCapacity(
            HQSpeakerServerConfig.maxAssetBytes(), MountConstants.MINIMUM_FILE_SIZE);
        mount = ComputerCraftAPI.createSaveDirMount(
            server,
            "hqspeaker/staging/" + stagingId,
            maxStagedBytes
        );
    }

    public void attach(IComputerAccess computer) {
        int id = computer.getID();
        String desired = "hqspeaker_" + stagingId.toString().substring(0, 8);
        String location = computer.mountWritable(desired, mount, "hqspeaker");
        if (location == null) location = computer.mountWritable(desired + "_" + id, mount, "hqspeaker");
        if (location == null) return;

        bindings.put(id, new Binding(computer, location));
        attachedComputerIds.add(id);
    }

    public void detach(IComputerAccess computer) {
        int computerId = computer.getID();
        Binding binding = bindings.remove(computerId);
        if (binding != null) {
            try {
                computer.unmount(binding.location());
            } catch (RuntimeException ignored) {
            }
        }

        Set<UUID> owned;
        synchronized (ownershipLock) {
            attachedComputerIds.remove(computerId);
            owned = preparedByComputerId.remove(computerId);
        }
        releaseDetached(owned);
    }

    public String mountPath(IComputerAccess computer) throws LuaException {
        Binding binding = bindings.get(computer.getID());
        if (binding == null || binding.location() == null) {
            throw new LuaException("HQ speaker media mount is unavailable");
        }
        return binding.location();
    }

    public long maxStagedBytes() {
        return maxStagedBytes;
    }

    /** Import and analyze one staged file, returning a reusable asset UUID only when the committed bytes are supported. */
    public String prepareAsset(IComputerAccess computer, String path, boolean consume) throws LuaException {
        int computerId = computer.getID();
        StagedFile staged = openStaged(computer, path);
        MediaAssetStore store = assetStore();
        MediaAsset asset;

        // Commit the exact staged bytes first. Analysis then runs against the immutable store copy, so a ComputerCraft
        // program modifying the writable staging file cannot race metadata for one version against bytes from another.
        try (SeekableByteChannel channel = staged.channel()) {
            asset = store.importAsset(staged.path(), staged.sizeBytes(), channel);
        } catch (IOException | RuntimeException e) {
            throw new LuaException("cannot prepare staged media: " + safeMessage(e));
        }

        try (SeekableByteChannel committed = store.openRead(asset.id())) {
            MediaMetadata metadata = ModernFiniteMediaAnalyzer.analyze(committed);
            asset.attachMetadata(metadata);
        } catch (IOException | RuntimeException e) {
            releaseReferenceBestEffort(asset.id(), "rejected media asset");
            throw new LuaException("cannot prepare staged media: " + safeMessage(e));
        }

        // The shared asset is already valid at this point. A temporary staging-delete failure must not destroy or hide
        // that valid asset. The bundled Lua helper also retries deletion from the mounted filesystem.
        if (consume) {
            try {
                mount.delete(staged.path());
            } catch (IOException deleteFailure) {
                HQSpeakerMod.warn("prepared media but could not remove staging file " + staged.path()
                    + ": " + safeMessage(deleteFailure));
            }
        }

        synchronized (ownershipLock) {
            if (!attachedComputerIds.contains(computerId)) {
                releaseReferenceBestEffort(asset.id(), "asset prepared by detached computer");
                throw new LuaException("computer detached while preparing media");
            }
            preparedByComputerId.computeIfAbsent(computerId, ignored -> new HashSet<>()).add(asset.id());
        }
        return asset.id().toString();
    }

    /** Return server-derived format/duration facts for a prepared asset. */
    public Map<String, Object> preparedInfo(String assetId) throws LuaException {
        UUID id = parseAssetId(assetId);
        MediaAsset asset = assetStore().get(id).orElseThrow(() -> new LuaException("unknown or released media asset"));
        MediaMetadata metadata = asset.metadata();
        if (metadata == null) throw new LuaException("media asset has not been analyzed");
        return metadata.toLuaMap(asset.sizeBytes(), asset.sourceName());
    }

    /** Release one prepared-owner reference belonging to this computer id. */
    public boolean releasePrepared(IComputerAccess computer, String assetId) throws LuaException {
        UUID id = parseAssetId(assetId);
        int computerId = computer.getID();

        // Check ownership before touching shared services. If service lookup fails, ownership is still intact for retry.
        synchronized (ownershipLock) {
            Set<UUID> assets = preparedByComputerId.get(computerId);
            if (assets == null || !assets.contains(id)) return false;
        }
        MediaAssetReleaseQueue releases = mediaAssets().releases();

        // Detach may race between the check and this removal. In that case detach already took responsibility.
        synchronized (ownershipLock) {
            Set<UUID> assets = preparedByComputerId.get(computerId);
            if (assets == null || !assets.remove(id)) return false;
            if (assets.isEmpty()) preparedByComputerId.remove(computerId);
        }

        MediaAssetReleaseQueue.Result result = releases.release(id);
        logReleaseResult(result, id, "prepared media asset");
        return true;
    }

    public StagedFile openStaged(IComputerAccess computer, String path) throws LuaException {
        Binding binding = bindings.get(computer.getID());
        if (binding == null) throw new LuaException("HQ speaker media mount is unavailable");

        String safePath;
        try {
            safePath = FiniteMediaPath.normalize(path);
        } catch (IllegalArgumentException e) {
            throw new LuaException("invalid staged path: " + e.getMessage());
        }

        try {
            if (!mount.exists(safePath) || mount.isDirectory(safePath)) {
                throw new LuaException("staged media file does not exist");
            }
            long size = mount.getSize(safePath);
            if (size <= 0L) throw new LuaException("staged media file is empty");
            if (size > maxStagedBytes()) {
                throw new LuaException("staged media file exceeds configured per-asset limit");
            }
            return new StagedFile(safePath, size, mount.openForRead(safePath));
        } catch (IOException e) {
            throw new LuaException("cannot open staged media: " + safeMessage(e));
        }
    }

    public void deleteStaged(String safePath) throws IOException {
        mount.delete(safePath);
    }

    public MediaAssetStore assetStore() throws LuaException {
        return mediaAssets().store();
    }

    private ServerMediaAssets mediaAssets() throws LuaException {
        try {
            return ServerMediaAssets.get(server);
        } catch (IOException e) {
            throw new LuaException("HQ media asset store is unavailable: " + safeMessage(e));
        }
    }

    public void cleanup() {
        for (Binding binding : new ArrayList<>(bindings.values())) detach(binding.computer());
        bindings.clear();

        Map<Integer, Set<UUID>> leftover;
        synchronized (ownershipLock) {
            attachedComputerIds.clear();
            leftover = new HashMap<>(preparedByComputerId);
            preparedByComputerId.clear();
        }
        for (Set<UUID> assets : leftover.values()) releaseDetached(assets);
    }

    private void releaseDetached(Set<UUID> assets) {
        if (assets == null || assets.isEmpty()) return;
        MediaAssetReleaseQueue releases;
        try {
            releases = ServerMediaAssets.get(server).releases();
        } catch (IOException e) {
            HQSpeakerMod.warn("could not open media asset services while releasing detached assets: " + e.getMessage());
            return;
        }
        for (UUID id : assets) {
            MediaAssetReleaseQueue.Result result = releases.release(id);
            logReleaseResult(result, id, "detached prepared media");
        }
    }

    private void releaseReferenceBestEffort(UUID id, String context) {
        try {
            MediaAssetReleaseQueue.Result result = ServerMediaAssets.get(server).releases().release(id);
            logReleaseResult(result, id, context);
        } catch (IOException e) {
            HQSpeakerMod.warn("could not open media asset services while releasing " + context + " " + id
                + ": " + safeMessage(e));
        }
    }

    private static void logReleaseResult(MediaAssetReleaseQueue.Result result, UUID id, String context) {
        if (result.status() == MediaAssetReleaseQueue.Status.MISSING) {
            HQSpeakerMod.warn(context + " was already missing " + id);
        } else if (result.deferred()) {
            HQSpeakerMod.warn(context + " release queued for retry " + id + ": " + result.error());
        }
    }

    public static UUID parseAssetId(String assetId) throws LuaException {
        if (assetId == null || assetId.isBlank()) throw new LuaException("asset id is required");
        try {
            return UUID.fromString(assetId);
        } catch (IllegalArgumentException e) {
            throw new LuaException("invalid media asset id");
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
