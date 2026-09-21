-- Frozen-v10 finite lifecycle regressions.
-- Usage: p0_finite_regression <mp3>

local args = {...}
assert(args[1], "usage: p0_finite_regression <mp3>")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")

local handle = assert(fs.open(args[1], "rb"), "cannot open " .. args[1])
local mp3 = handle.readAll()
handle.close()

local function waitFor(state, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local s = speaker.audioStatus()
    if s.state == state then return s end
    if s.state == "error" then error("audio error: " .. tostring(s.error)) end
    sleep(0.05)
  end
  error("timed out waiting for " .. state)
end

speaker.audioStop()
assert(speaker.speakMp3(mp3, 0.5), "MP3 rejected")
local started = waitFor("playing", 15)
assert(started.duration and started.duration > 1, "need a finite MP3 longer than 1 second")

assert(speaker.audioSetLooping(true), "failed to enable loop")
local nearEnd = math.max(0, started.duration - 0.35)
assert(speaker.audioSeek(nearEnd), "seek near end failed")

local previous = nearEnd
local wrapped = false
local deadline = os.epoch("utc") + 5000
while os.epoch("utc") < deadline do
  sleep(0.03)
  local s = speaker.audioStatus()
  assert(s.state == "playing", "loop stopped before wrap")
  if s.position + 0.15 < previous then wrapped = true break end
  previous = s.position
end
assert(wrapped, "did not observe loop wrap")

local beforeDisable = speaker.audioStatus().position
assert(speaker.audioSetLooping(false), "failed to disable loop")
sleep(0.05)
local afterDisable = speaker.audioStatus()
assert(afterDisable.state == "playing", "disabling loop changed playback state")
assert(afterDisable.position < afterDisable.duration - 0.05, "disabling loop jumped to duration")
assert(math.abs(afterDisable.position - beforeDisable) < 0.5, "disabling loop lost wrapped position")

assert(speaker.audioSeek(afterDisable.duration), "seek exactly to duration failed")
local ended = waitFor("ended", 5)
assert(math.abs(ended.position - ended.duration) < 0.1, "ended position was not duration")
assert(speaker.speakIsPlaying() == false, "speakIsPlaying stayed true after end")

speaker.audioStop()
print("[PASS] P0 finite regressions")
