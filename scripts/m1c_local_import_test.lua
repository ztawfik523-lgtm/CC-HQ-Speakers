-- M1C runtime acceptance for CC-local staging -> reusable server asset -> prepared playback.
-- Usage: m1c_local_import_test <path-to-mp3/ogg/wav>

local args = {...}
local path = assert(args[1], "usage: m1c_local_import_test <path-to-mp3/ogg/wav>")
local speaker = assert(peripheral.find("speaker"), "attach an HQ speaker")
local hq = require("hqspeaker")

assert(type(hq.prepareFile) == "function", "hqspeaker.prepareFile is missing")
assert(type(hq.playPrepared) == "function", "hqspeaker.playPrepared is missing")
assert(type(hq.releasePrepared) == "function", "hqspeaker.releasePrepared is missing")
assert(type(speaker.audioPrepareStaged) == "function", "audioPrepareStaged is missing")
assert(type(speaker.audioPlayPrepared) == "function", "audioPlayPrepared is missing")
assert(type(speaker.audioReleasePrepared) == "function", "audioReleasePrepared is missing")

assert(fs.exists(path) and not fs.isDir(path), "fixture file does not exist")
local size = fs.getSize(path)
assert(size > 0, "fixture is empty")
assert(size <= speaker.audioMaxStagedBytes(), "fixture exceeds current staging/import ceiling")

local function status()
    return speaker.audioStatus()
end

local function waitForState(wanted, timeout)
    local deadline = os.epoch("utc") + math.floor((timeout or 30) * 1000)
    while os.epoch("utc") < deadline do
        local s = status()
        if s.state == wanted then return s end
        sleep(0.05)
    end
    error("timed out waiting for " .. wanted .. ": " .. textutils.serialize(status(), { compact = true }))
end

speaker.stop()
sleep(0.1)

-- A prepared but unused asset has one preparation reference. Releasing it should remove that ownership cleanly.
local unused = hq.prepareFile(speaker, path)
assert(type(unused) == "string" and #unused > 0, "prepareFile did not return an asset id")
assert(hq.releasePrepared(speaker, unused) == true, "could not release unused prepared asset")
assert(hq.releasePrepared(speaker, unused) == false, "double release unexpectedly succeeded")
local deadOk = pcall(speaker.audioPlayPrepared, unused, 0.2)
assert(deadOk == false, "released asset could still be started")

-- Prepare again, start it, then release only the preparation reference.
local asset = hq.prepareFile(speaker, path)
assert(type(asset) == "string" and #asset > 0, "second prepareFile did not return an asset id")
assert(hq.playPrepared(speaker, asset, { volume = 0.5 }) == true, "prepared asset was rejected")
assert(hq.releasePrepared(speaker, asset) == true, "could not release preparation reference after play")

local active = waitForState("playing", 30)
assert(active.assetId == asset, "audioStatus does not identify the prepared asset being played")
assert(speaker.speakIsPlaying(), "speaker stopped when only the preparation reference was released")

speaker.audioStop()
local stopped = status()
assert(stopped.state == "idle" or not speaker.speakIsPlaying(), "prepared playback did not stop")

-- Convenience helper must do prepare -> play -> release internally without exposing/leaking the temporary owner.
assert(hq.playFile(speaker, path, { volume = 0.4 }) == true, "hqspeaker.playFile convenience path failed")
local convenience = waitForState("playing", 30)
assert(type(convenience.assetId) == "string" and #convenience.assetId > 0,
    "playFile did not route playback through a prepared asset")

speaker.stop()
print("M1C local import contract passed")
