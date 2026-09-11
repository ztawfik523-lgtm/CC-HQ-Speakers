local args = {...}
local path = assert(args[1], "usage: m1a_local_file_test <path-to-mp3/ogg/wav>")
local speaker = assert(peripheral.find("speaker"), "attach an HQ speaker")
local hq = require("hqspeaker")

local function status()
    return speaker.audioStatus()
end

local function waitForState(wanted, timeout)
    local deadline = os.epoch("utc") + math.floor((timeout or 20) * 1000)
    while os.epoch("utc") < deadline do
        local s = status()
        if s.state == wanted then return s end
        sleep(0.05)
    end
    error("timed out waiting for state " .. wanted .. ": " .. textutils.serialize(status(), { compact = true }))
end

local size = fs.getSize(path)
print(("Testing %s (%d bytes / %.2f MiB)"):format(path, size, size / 1024 / 1024))
print(("HQ staged-file ceiling: %.0f MiB"):format(speaker.audioMaxStagedBytes() / 1024 / 1024))

assert(type(speaker.stop) == "function", "standard CC speaker.stop is missing")
speaker.stop()
assert(speaker.playNote("harp"), "standard playNote with omitted optional args failed")
sleep(0.1)
speaker.stop()

assert(hq.playFile(speaker, path, { volume = 0.6 }), "local file was rejected")
local playing = waitForState("playing", 30)
assert(playing.observed == true, "renderer was not observed")
assert(type(playing.duration) == "number" and playing.duration > 0, "finite duration was not reported")
print(("Started: duration %.3fs"):format(playing.duration))

assert(speaker.audioPause(), "pause failed")
local paused = waitForState("paused", 5)
local p0 = paused.position
sleep(0.5)
local p1 = status().position
assert(math.abs(p1 - p0) < 0.20, "position advanced while paused")

assert(speaker.audioResume(), "resume failed")
waitForState("playing", 5)

if playing.duration > 3.0 then
    local target = math.min(playing.duration - 1.5, math.max(0.5, playing.duration * 0.35))
    assert(speaker.audioSeek(target), "seek failed")
    local afterSeek = status()
    assert(math.abs(afterSeek.position - target) < 0.75, "seek position was not applied")

    assert(speaker.audioSetLooping(true), "loop enable failed")
    assert(speaker.audioSeek(math.max(0, playing.duration - 0.6)), "seek near loop end failed")
    sleep(1.0)
    local wrapped = status()
    assert(wrapped.position < math.min(2.0, playing.duration), "loop did not wrap")

    assert(speaker.audioSetLooping(false), "loop disable failed")
    local disabled = status()
    assert(disabled.position < playing.duration - 0.1, "disabling loop jumped to EOF")
end

assert(speaker.audioSetLooping(false), "loop disable failed")
assert(speaker.audioSeek(playing.duration), "exact-duration seek was rejected")
local ended = waitForState("ended", 5)
assert(math.abs(ended.position - ended.duration) < 0.10, "ended position is not duration")
assert(not speaker.speakIsPlaying(), "speaker still reports active after exact-end seek")

speaker.stop()
print("M1A local finite test passed")
