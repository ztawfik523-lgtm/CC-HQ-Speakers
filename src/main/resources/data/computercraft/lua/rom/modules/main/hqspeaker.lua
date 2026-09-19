-- HQ Speakers finite-file helper.
--
-- Recommended high-level API for ComputerCraft-local finite files:
--   hqspeaker.playFile(speaker, path [, options])
--   hqspeaker.prepareFile(speaker, path)
--   hqspeaker.preparedInfo(speaker, assetId)
--   hqspeaker.playPrepared(speaker, assetId [, options])
--   hqspeaker.releasePrepared(speaker, assetId)
--
-- A CC-visible file is copied into temporary writable staging, imported as one reusable
-- server-owned media asset, and then played from that asset. Staging is import plumbing,
-- not the persistent media/playback model.
--
-- The old peripheral-level audioPlayStaged() prototype was removed in M1F. This module
-- uses the reusable prepare/play/release asset path only.
--
-- Full user-facing reference: docs/LUA-API.md in the project repository.

local hqspeaker = {}

local function checkSpeaker(speaker)
    if type(speaker) ~= "table"
        or type(speaker.audioMountPath) ~= "function"
        or type(speaker.audioPrepareStaged) ~= "function"
        or type(speaker.audioPreparedInfo) ~= "function"
        or type(speaker.audioPlayPrepared) ~= "function"
        or type(speaker.audioReleasePrepared) ~= "function"
        or type(speaker.audioMaxStagedBytes) ~= "function" then
        error("expected an HQ speaker peripheral with prepared-media support", 3)
    end
end

local function checkLocalFile(speaker, path)
    checkSpeaker(speaker)
    if type(path) ~= "string" then error("path must be a string", 3) end
    if not fs.exists(path) or fs.isDir(path) then error("file does not exist: " .. path, 3) end

    local size = fs.getSize(path)
    local maxSize = speaker.audioMaxStagedBytes()
    if size <= 0 then error("file is empty: " .. path, 3) end
    if size > maxSize then
        error(("file is too large (%d bytes; maximum %d bytes)"):format(size, maxSize), 3)
    end
end

local function stageFile(speaker, path)
    checkLocalFile(speaker, path)

    local mount = speaker.audioMountPath()
    local ext = path:match("(%.[%w_%-]+)$") or ".media"
    local token = tostring(os.epoch("utc")) .. "-" .. tostring(math.random(0, 0x7fffffff))
    local stagedName = ".hqspeaker-" .. token .. ext
    local stagedPath = fs.combine(mount, stagedName)

    local copied, copyError = pcall(fs.copy, path, stagedPath)
    if not copied then
        -- fs.copy may have created a partial destination before failing. Never leave that staged partial behind.
        pcall(fs.delete, stagedPath)
        error(copyError, 3)
    end

    return stagedName, stagedPath
end

--- Prepare and analyze a finite CC-local file without starting playback.
---
--- The returned asset ID represents one reusable server-owned media asset and one
--- preparation-owner reference held by this ComputerCraft computer. The file is not
--- tied to one playback: a later successful play takes its own separate playback reference.
---
--- Call releasePrepared when this program no longer needs preparation ownership.
---
--- @param speaker table A wrapped HQ-capable normal ComputerCraft speaker.
--- @param path string Path in the ComputerCraft filesystem.
--- @return string assetId Server media asset ID.
function hqspeaker.prepareFile(speaker, path)
    local stagedName, stagedPath = stageFile(speaker, path)
    local ok, assetOrError = pcall(speaker.audioPrepareStaged, stagedName, true)
    if not ok then
        pcall(fs.delete, stagedPath)
        error(assetOrError, 2)
    end

    -- The server normally consumes the temporary staging file after importing it.
    -- Retry from the mounted filesystem too so a successful server asset is never
    -- discarded merely because the first staging cleanup attempt failed.
    pcall(fs.delete, stagedPath)
    return assetOrError
end

--- Return server-derived facts for a prepared finite asset.
---
--- Current fields include format, duration, sampleRate, channels, bitsPerSample,
--- sizeBytes, and sourceName. These are facts about the server asset; client decoder
--- guesses are not the authoritative finite duration source.
---
--- @param speaker table A wrapped HQ-capable normal ComputerCraft speaker.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @return table info Server-derived media information.
function hqspeaker.preparedInfo(speaker, assetId)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    return speaker.audioPreparedInfo(assetId)
end

--- Start a previously prepared server asset on this speaker.
---
--- A successful playback takes its own asset reference before returning true, so the
--- preparation reference may be released afterward without stopping the active playback.
--- Starting another incompatible HQ continuous source follows the speaker's normal
--- replacement semantics.
---
--- @param speaker table A wrapped HQ-capable normal ComputerCraft speaker.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @param options? table Optional table, currently { volume = number }.
--- @return boolean accepted True when playback was accepted.
function hqspeaker.playPrepared(speaker, assetId, options)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then error("volume must be a number", 2) end
    return speaker.audioPlayPrepared(assetId, volume)
end

--- Start one shared prepared playback on the speakers currently attached to this computer.
---
--- The speaker set is snapshotted when the call starts. The endpoints share one canonical
--- timeline but retain independent physical position, listener membership, renderer,
--- recovery, and volume.
---
--- @param speaker table Any wrapped HQ-capable normal ComputerCraft speaker attached to this computer.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @param options? table Optional table, currently { volume = number }.
--- @return boolean accepted True when the multispeaker playback was accepted.
function hqspeaker.playPreparedAll(speaker, assetId, options)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then error("volume must be a number", 2) end
    return speaker.audioPlayPreparedAll(assetId, volume)
end

--- Release this ComputerCraft computer's preparation reference to an asset.
---
--- This does not stop a currently playing speaker which already retained a separate
--- playback reference. Returns false if this computer does not own that preparation.
---
--- @param speaker table A wrapped HQ-capable normal ComputerCraft speaker.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @return boolean released True when this computer's preparation reference was released.
function hqspeaker.releasePrepared(speaker, assetId)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    return speaker.audioReleasePrepared(assetId)
end

--- Convenience helper: prepare one local file, start playback, then release only the
--- temporary preparation reference.
---
--- This is the recommended one-call path for a normal ComputerCraft-local finite file.
--- Internally the active playback has already retained its own server asset reference,
--- so the encoded asset remains alive while playback is active.
---
--- @param speaker table A wrapped HQ-capable normal ComputerCraft speaker.
--- @param path string Path in the ComputerCraft filesystem.
--- @param options? table Optional table, currently { volume = number }.
--- @return boolean accepted True when playback was accepted.
function hqspeaker.playFile(speaker, path, options)
    local assetId = hqspeaker.prepareFile(speaker, path)

    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then
        pcall(speaker.audioReleasePrepared, assetId)
        error("volume must be a number", 2)
    end

    local playOk, acceptedOrError = pcall(speaker.audioPlayPrepared, assetId, volume)
    local releaseOk, releasedOrError = pcall(speaker.audioReleasePrepared, assetId)

    if not playOk then
        if not releaseOk then
            error(tostring(acceptedOrError) .. "; also failed to release prepared asset: " .. tostring(releasedOrError), 2)
        end
        error(acceptedOrError, 2)
    end
    if not releaseOk then error(releasedOrError, 2) end
    if releasedOrError ~= true then error("prepared asset ownership was lost before release", 2) end

    return acceptedOrError == true
end

--- Convenience helper: prepare one local file and start one shared playback on the
--- speakers currently attached to this ComputerCraft computer.
---
--- @param speaker table Any wrapped HQ-capable normal ComputerCraft speaker attached to this computer.
--- @param path string Path in the ComputerCraft filesystem.
--- @param options? table Optional table, currently { volume = number }.
--- @return boolean accepted True when multispeaker playback was accepted.
function hqspeaker.playFileAll(speaker, path, options)
    local assetId = hqspeaker.prepareFile(speaker, path)

    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then
        pcall(speaker.audioReleasePrepared, assetId)
        error("volume must be a number", 2)
    end

    local playOk, acceptedOrError = pcall(speaker.audioPlayPreparedAll, assetId, volume)
    local releaseOk, releasedOrError = pcall(speaker.audioReleasePrepared, assetId)

    if not playOk then
        if not releaseOk then
            error(tostring(acceptedOrError) .. "; also failed to release prepared asset: " .. tostring(releasedOrError), 2)
        end
        error(acceptedOrError, 2)
    end
    if not releaseOk then error(releasedOrError, 2) end
    if releasedOrError ~= true then error("prepared asset ownership was lost before release", 2) end

    return acceptedOrError == true
end

return hqspeaker
