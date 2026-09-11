-- HQ Speakers finite-file helper.
-- Copies a ComputerCraft-visible file into temporary writable staging, prepares one reusable
-- server media asset, and lets Lua decide when/how often that asset is played.

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
--- The returned asset ID owns one prepared reference. Call releasePrepared when no longer needed.
--- @param speaker table A wrapped HQ speaker peripheral.
--- @param path string Path in the CC filesystem.
--- @return string assetId
function hqspeaker.prepareFile(speaker, path)
    local stagedName, stagedPath = stageFile(speaker, path)
    local ok, assetOrError = pcall(speaker.audioPrepareStaged, stagedName, true)
    if not ok then
        pcall(fs.delete, stagedPath)
        error(assetOrError, 2)
    end

    -- The server normally consumes the staging file. Retry from the mounted filesystem too so a successful asset
    -- is never discarded merely because the first cleanup attempt failed.
    pcall(fs.delete, stagedPath)
    return assetOrError
end

--- Return server-derived facts for a prepared finite asset.
--- @param speaker table A wrapped HQ speaker peripheral.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @return table info format, duration, sampleRate, channels, bitsPerSample, sizeBytes, sourceName
function hqspeaker.preparedInfo(speaker, assetId)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    return speaker.audioPreparedInfo(assetId)
end

--- Start a previously prepared asset on this speaker.
--- Starting another incompatible HQ source follows the speaker's normal replacement semantics.
--- @param speaker table A wrapped HQ speaker peripheral.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @param options? table { volume = number }
--- @return boolean accepted
function hqspeaker.playPrepared(speaker, assetId, options)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then error("volume must be a number", 2) end
    return speaker.audioPlayPrepared(assetId, volume)
end

--- Release this computer's prepared reference to an asset.
--- A currently playing speaker keeps its own reference until that playback stops/ends/errors.
--- @param speaker table A wrapped HQ speaker peripheral.
--- @param assetId string Asset ID returned by prepareFile/audioPrepareStaged.
--- @return boolean released
function hqspeaker.releasePrepared(speaker, assetId)
    checkSpeaker(speaker)
    if type(assetId) ~= "string" then error("assetId must be a string", 2) end
    return speaker.audioReleasePrepared(assetId)
end

--- Convenience: prepare a local file, start it, then release the temporary prepared reference.
--- The playback itself retains the asset, so the encoded file remains alive while it is playing.
--- @param speaker table A wrapped HQ speaker peripheral.
--- @param path string Path in the CC filesystem.
--- @param options? table { volume = number }
--- @return boolean accepted
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

return hqspeaker
