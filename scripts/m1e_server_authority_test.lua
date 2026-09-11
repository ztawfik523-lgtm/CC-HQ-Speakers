local args = {...}
local path = assert(args[1], "usage: m1e_server_authority_test <small-mp3-or-wav>")
local speaker = assert(peripheral.find("speaker"), "attach an HQ speaker")
local hq = require("hqspeaker")

if not fs.exists(path) or fs.isDir(path) then error("file does not exist: " .. path, 0) end

local asset
local function cleanup()
    pcall(speaker.audioStop)
    if asset then pcall(hq.releasePrepared, speaker, asset) end
end

local function approx(a, b, tolerance)
    return math.abs(a - b) <= tolerance
end

local ok, err = xpcall(function()
    asset = hq.prepareFile(speaker, path)
    local info = hq.preparedInfo(speaker, asset)
    assert(type(info.duration) == "number" and info.duration > 1.0,
        "use a fixture longer than one second for the timing checks")

    assert(hq.playPrepared(speaker, asset, { volume = 0.4 }), "prepared playback was rejected")
    local initial = speaker.audioStatus()
    assert(initial.state == "playing", "finite play did not become server PLAYING immediately: " .. tostring(initial.state))
    assert(initial.assetId == asset, "active status lost prepared asset ID")
    assert(approx(initial.duration, info.duration, 0.001), "server playback duration changed from prepared metadata")

    local p0 = initial.position
    sleep(0.30)
    local moving = speaker.audioStatus()
    assert(moving.state == "playing", "playback left PLAYING while server clock should advance")
    assert(moving.position > p0 + 0.10, "server position did not advance independently of renderer readiness")

    assert(speaker.audioPause(), "pause was rejected")
    local paused = speaker.audioStatus()
    assert(paused.state == "paused", "pause did not become canonical PAUSED")
    local pausedAt = paused.position
    sleep(0.30)
    local stillPaused = speaker.audioStatus()
    assert(approx(stillPaused.position, pausedAt, 0.05), "server position advanced while paused")

    assert(speaker.audioResume(), "resume was rejected")
    sleep(0.20)
    local resumed = speaker.audioStatus()
    assert(resumed.state == "playing", "resume did not restore canonical PLAYING")
    assert(resumed.position > pausedAt + 0.05, "server position did not advance after resume")

    assert(speaker.audioSeek(info.duration), "exact-duration seek was rejected")
    local ended = speaker.audioStatus()
    assert(ended.state == "ended", "non-looping seek(duration) did not end immediately")
    assert(approx(ended.position, info.duration, 0.001), "ended position was not clamped to duration")

    -- The prepared owner still exists even though the first playback reference ended, so replay is legal.
    assert(hq.playPrepared(speaker, asset, { volume = 0.4 }), "replay after terminal state was rejected")
    assert(speaker.audioSetLooping(true), "loop enable was rejected")
    assert(speaker.audioSeek(info.duration), "looping exact-duration seek was rejected")
    local looped = speaker.audioStatus()
    assert(looped.state == "playing", "looping exact-duration seek incorrectly ended playback")
    assert(looped.position < 0.10, "looping exact-duration seek did not wrap to the start")

    speaker.audioStop()
    assert(hq.releasePrepared(speaker, asset), "prepared asset release failed")
    asset = nil
end, debug.traceback)

cleanup()
if not ok then error(err, 0) end
print("M1E server-authority contract passed")
