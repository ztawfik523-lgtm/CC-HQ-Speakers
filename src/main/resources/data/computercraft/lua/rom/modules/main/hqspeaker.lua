-- HQ Speakers finite-file helper.
-- Copies a ComputerCraft-visible file into the speaker's writable staging mount without readAll().

local hqspeaker = {}

local function checkSpeaker(speaker)
    if type(speaker) ~= "table" or type(speaker.audioMountPath) ~= "function" or type(speaker.audioPlayStaged) ~= "function" then
        error("expected an HQ speaker peripheral", 3)
    end
end

--- Play a finite file already visible in the ComputerCraft filesystem.
--- @param speaker table A wrapped speaker peripheral.
--- @param path string Path in the CC filesystem.
--- @param options? table { volume = number }
--- @return boolean accepted
function hqspeaker.playFile(speaker, path, options)
    checkSpeaker(speaker)
    if type(path) ~= "string" then error("path must be a string", 2) end
    if not fs.exists(path) or fs.isDir(path) then error("file does not exist: " .. path, 2) end

    options = options or {}
    local volume = options.volume
    if volume ~= nil and type(volume) ~= "number" then error("volume must be a number", 2) end

    local size = fs.getSize(path)
    local maxSize = speaker.audioMaxStagedBytes()
    if size <= 0 then error("file is empty: " .. path, 2) end
    if size > maxSize then
        error(("file is too large (%d bytes; maximum %d bytes)"):format(size, maxSize), 2)
    end

    local mount = speaker.audioMountPath()
    local ext = path:match("(%.[%w_%-]+)$") or ".media"
    local token = tostring(os.epoch("utc")) .. "-" .. tostring(math.random(0, 0x7fffffff))
    local stagedName = ".hqspeaker-" .. token .. ext
    local stagedPath = fs.combine(mount, stagedName)

    fs.copy(path, stagedPath)
    local ok, accepted = pcall(speaker.audioPlayStaged, stagedName, volume, true)
    if not ok or not accepted then
        pcall(fs.delete, stagedPath)
        if not ok then error(accepted, 2) end
        return false
    end
    return true
end

return hqspeaker
