-- Current protocol-v10 automated core acceptance.
-- Usage: v10_core_acceptance <mp3> [wav]
--
-- This checks API truthfulness and server-visible behavior. It cannot prove
-- audibility, SoundManager/OpenAL behavior, moving-ship projection, or spatial sync by itself.

local args = {...}
assert(args[1], "usage: v10_core_acceptance <mp3> [wav]")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local peripheralName = assert(peripheral.getName(speaker), "could not resolve speaker peripheral name")

local methods = {}
for _, name in ipairs(peripheral.getMethods(peripheralName) or {}) do
  methods[name] = true
end

local required = {
  "playNote", "playSound", "playAudio", "stop",
  "speakMp3", "speakWav", "speakMp3All", "speakMp3At",
  "speakPCM", "speakPCMAll", "speakPCMAt", "speakStop",
  "speakMaxSamples", "speakSampleRate", "speakSupportedFiles",
  "audioStatus", "audioStatusAt", "audioPause", "audioResume", "audioSeek",
  "audioSetVolume", "audioSetLooping", "audioStop", "audioStopAll", "audioStopAt",
  "speakStream", "speakStreamAll", "speakStreamAt", "getStreamFormats",
  "getSpeakerCount",
}
for _, name in ipairs(required) do
  assert(methods[name], "missing current API method: " .. name)
end

for _, name in ipairs({
  "speakOgg", "speakAudio", "speakFile", "speakPacked",
  "speakStopAll", "speakStopAt", "speakVolumeAll", "setLooping", "setLoopingAll",
  "speakHLS", "speakHLSAll", "speakHLSAt",
  "speakTS", "speakTSAll", "speakTSAt",
}) do
  assert(not methods[name], "retired API unexpectedly exposed: " .. name)
end

assert(speaker.speakMaxSamples() == 131072, "speakMaxSamples must be 131072")
assert(speaker.speakSampleRate() == 48000, "speakSampleRate must be 48000")

local supported = {}
for _, ext in pairs(speaker.speakSupportedFiles()) do supported[ext] = true end
assert(supported.mp3 and supported.wav, "supported finite formats must include mp3 and wav")
assert(not supported.ogg, "OGG must not be advertised as supported")

local streamFormats = speaker.getStreamFormats()
assert(type(streamFormats) == "table" and streamFormats.mp3,
  "MP3/ICY radio must be advertised")
assert(streamFormats.hls == nil and streamFormats.ts == nil,
  "retired HLS/TS formats must not be advertised")

local function readBinary(path)
  local handle = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = handle.readAll()
  handle.close()
  return data
end

local mp3 = readBinary(args[1])
local wav = args[2] and readBinary(args[2]) or nil

local function waitForStatus(getStatus, wanted, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local status = getStatus()
    if status.state == wanted then return status end
    if status.state == "error" then
      error("finite playback error: " .. tostring(status.error))
    end
    sleep(0.05)
  end
  error("timed out waiting for state " .. wanted)
end

local function waitMain(wanted, timeout)
  return waitForStatus(function() return speaker.audioStatus() end, wanted, timeout)
end

print("v10 core acceptance: " .. peripheralName)

-- Standard CC:T surface: basic dispatch only. Detailed backpressure is covered by
-- p0_cc_speaker_contract.lua.
assert(type(speaker.playNote("harp", 0.2, 1.0)) == "boolean", "playNote did not return boolean")
speaker.stop()
sleep(0.05)

-- Singular finite MP3.
speaker.audioStop()
assert(speaker.speakMp3(mp3, 0.4), "speakMp3 rejected valid test file")
local playing = waitMain("playing", 15)
assert(type(playing.duration) == "number" and playing.duration > 0, "finite duration missing")

assert(speaker.audioPause(), "audioPause failed")
waitMain("paused", 5)
assert(speaker.audioResume(), "audioResume failed")
waitMain("playing", 5)

local seekTarget = math.min(1.0, math.max(0, playing.duration * 0.25))
assert(speaker.audioSeek(seekTarget), "audioSeek failed")
assert(speaker.audioSetVolume(0.35), "audioSetVolume failed")
assert(speaker.audioSetLooping(true), "audioSetLooping(true) failed")
assert(speaker.audioSetLooping(false), "audioSetLooping(false) failed")
speaker.audioStop()
waitMain("idle", 5)

-- Optional WAV check.
if wav then
  assert(speaker.speakWav(wav, 0.35), "speakWav rejected valid test file")
  waitMain("playing", 15)
  speaker.audioStop()
  waitMain("idle", 5)
end

-- RAW signed-16 path.
local pcm = {}
for i = 1, 2400 do
  pcm[i] = math.floor(math.sin(i * 0.08) * 24000)
end
assert(speaker.speakPCM(pcm, 0.25), "speakPCM rejected a small signed-16 chunk")
sleep(0.1)
speaker.speakStop()

-- Multispeaker shared finite + endpoint-local audioStopAt semantics.
local count = speaker.getSpeakerCount()
print("attached speaker count: " .. count)
if count >= 2 then
  speaker.audioStop()
  assert(speaker.speakMp3All(mp3, 0.35), "speakMp3All rejected")

  local first = waitForStatus(function() return speaker.audioStatusAt(1) end, "playing", 15)
  local second = waitForStatus(function() return speaker.audioStatusAt(2) end, "playing", 15)
  if first.playbackId and second.playbackId then
    assert(first.playbackId == second.playbackId, "All endpoints did not share one playback")
  end

  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 5)

  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) incorrectly stopped the remaining shared playback")

  speaker.audioStopAll()
  waitForStatus(function() return speaker.audioStatusAt(1) end, "idle", 5)

  assert(speaker.speakPCMAll(pcm, 0.2), "speakPCMAll rejected a small group chunk")
  sleep(0.15) -- allow the short RAW chunk to drain naturally
else
  print("multispeaker checks skipped: attach at least two speakers to this computer")
end

speaker.audioStop()
speaker.speakStop()
print("v10 automated core acceptance passed")
print("Still required manually: audibility/spatial sync, listener walk-in/out, reload recovery, Sable/VS2 movement, Sound Physics.")
