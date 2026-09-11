local args = {...}
local path = assert(args[1], "usage: m1d_media_analysis_test <small-mp3/ogg/wav/aiff/au>")
local speaker = assert(peripheral.find("speaker"), "attach an HQ speaker")
local hq = require("hqspeaker")

local expectedByExt = {
    mp3 = "mp3",
    ogg = "ogg",
    wav = "wav",
    aiff = "aiff",
    aif = "aiff",
    au = "au",
    snd = "au",
}

local ext = path:match("%.([^./]+)$")
local expected = ext and expectedByExt[ext:lower()] or nil
if not expected then error("test input extension must identify one supported test format", 0) end
if not fs.exists(path) or fs.isDir(path) then error("file does not exist: " .. path, 0) end

local suffix = tostring(os.epoch("utc"))
local disguised = ".hqspeaker-m1d-" .. suffix .. ".definitely-not-the-real-extension"
local invalid = ".hqspeaker-m1d-invalid-" .. suffix .. ".mp3"

local function cleanup()
    pcall(fs.delete, disguised)
    pcall(fs.delete, invalid)
end

local ok, err = xpcall(function()
    -- Prove analysis uses file bytes rather than the filename extension.
    fs.copy(path, disguised)
    local asset = hq.prepareFile(speaker, disguised)
    local info = hq.preparedInfo(speaker, asset)

    assert(type(info) == "table", "preparedInfo did not return a table")
    assert(info.format == expected, ("expected format %s, got %s"):format(expected, tostring(info.format)))
    assert(type(info.duration) == "number" and info.duration > 0, "duration was not analyzed")
    assert(type(info.sampleRate) == "number" and info.sampleRate > 0, "sample rate was not analyzed")
    assert(type(info.channels) == "number" and info.channels >= 1 and info.channels <= 8,
        "channel count was not analyzed")
    assert(type(info.sizeBytes) == "number" and info.sizeBytes == fs.getSize(path), "encoded size mismatch")

    print(("Detected %s: %.3fs, %d Hz, %d channel(s)")
        :format(info.format, info.duration, info.sampleRate, info.channels))

    -- The current playback bridge should use the same server-derived facts immediately, before a client reports in.
    assert(hq.playPrepared(speaker, asset, { volume = 0.5 }), "prepared playback was rejected")
    local status = speaker.audioStatus()
    assert(status.assetId == asset, "playback status lost the asset ID")
    assert(status.format == info.format, "playback format disagrees with prepared metadata")
    assert(math.abs(status.duration - info.duration) < 0.001, "playback duration disagrees with prepared metadata")
    assert(status.sampleRate == info.sampleRate, "playback sample rate disagrees with prepared metadata")
    assert(status.channels == info.channels, "playback channel count disagrees with prepared metadata")

    -- Releasing preparation must not kill the playback's separate reference.
    assert(hq.releasePrepared(speaker, asset), "prepared reference did not release")
    assert(speaker.audioStatus().assetId == asset, "playing asset disappeared after prepared release")
    speaker.audioStop()

    -- A misleading extension must not make arbitrary bytes into a valid media asset.
    local handle = assert(fs.open(invalid, "wb"))
    handle.write("this is not an mp3, despite its filename")
    handle.close()
    local invalidOk = pcall(hq.prepareFile, speaker, invalid)
    assert(not invalidOk, "invalid bytes were accepted because of the .mp3 filename")

    -- The advertised finite list must match what M1D can actually analyze/decode.
    local files = speaker.speakSupportedFiles()
    assert(type(files) == "table", "speakSupportedFiles did not return a table")
    local set = {}
    for _, name in ipairs(files) do set[name] = true end
    for _, name in ipairs({ "mp3", "ogg", "wav", "aiff", "aif", "au", "snd" }) do
        assert(set[name], "supported finite list is missing " .. name)
    end
    for _, name in ipairs({ "mp2", "mp4", "m4a", "aac" }) do
        assert(not set[name], "unsupported finite list still advertises " .. name)
    end
end, debug.traceback)

cleanup()
if not ok then error(err, 0) end
print("M1D media analysis test passed")
