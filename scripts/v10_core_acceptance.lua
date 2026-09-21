-- Frozen protocol-v10 automated core acceptance.
-- Usage: v10_core_acceptance <mp3> [wav]
-- This validates API truthfulness and server-visible behavior. Manual runtime
-- gates still cover audibility, movement, reload/recovery, spatial sync and SPR.

local args = {...}
assert(args[1], "usage: v10_core_acceptance <mp3> [wav]")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local peripheralName = assert(peripheral.getName(speaker), "could not resolve speaker peripheral name")

local methods = {}
for _, name in ipairs(peripheral.getMethods(peripheralName) or {}) do methods[name] = true end

local required = {
  "playNote", "playSound", "playAudio", "stop",
  "playNoteAll", "playSoundAll", "playAudioAll",
  "playNoteAt", "playSoundAt", "playAudioAt",

  "audioMountPath", "audioPrepareStaged", "audioPreparedInfo", "audioPreparedFormats",
  "audioPlayPrepared", "audioPlayPreparedAll", "audioReleasePrepared", "audioMaxStagedBytes",

  "speakMp3", "speakWav", "speakMp3All", "speakWavAll", "speakMp3At", "speakWavAt",

  "audioStatus", "audioPause", "audioResume", "audioSeek", "audioSetVolume",
  "audioSetLooping", "audioStop", "audioSetMuted",
  "audioStatusAll", "audioPauseAll", "audioResumeAll", "audioSeekAll",
  "audioSetVolumeAll", "audioSetLoopingAll", "audioStopAll", "audioSetMutedAll",
  "audioStatusAt", "audioPauseAt", "audioResumeAt", "audioSeekAt",
  "audioSetVolumeAt", "audioSetLoopingAt", "audioStopAt", "audioSetMutedAt",

  "speakPCM", "speakPCMAll", "speakPCMAt", "speakStop", "speakVolume",
  "speakIsPlaying", "speakQueueSize", "speakSampleRate", "speakMaxAudioBytes",
  "speakMaxSamples", "speakSupportedFiles",

  "speakStream", "speakStreamAll", "speakStreamAt", "isStreaming",
  "getStreamUrl", "getStreamFormats", "getStreamMeta", "getStreamMetaSerial",

  "getPeripheralType", "getPos", "getSpeakerCount", "getSpeakers", "getSpeakerPos",
}
for _, name in ipairs(required) do
  assert(methods[name], "missing frozen API method: " .. name)
end

for _, name in ipairs({
  "speakOgg", "speakAudio", "speakFile", "speakPacked",
  "speakStopAll", "speakStopAt", "speakVolumeAll", "setLooping", "setLoopingAll",
  "speakHLS", "speakHLSAll", "speakHLSAt",
  "speakTS", "speakTSAll", "speakTSAt",
}) do
  assert(not methods[name], "retired API unexpectedly exposed: " .. name)
end

assert(speaker.getPeripheralType() == "speaker", "peripheral type changed")
assert(speaker.speakMaxSamples() == 131072, "speakMaxSamples must be 131072")
assert(speaker.speakSampleRate() == 48000, "speakSampleRate must be 48000")
assert(type(speaker.audioMaxStagedBytes()) == "number" and speaker.audioMaxStagedBytes() > 0,
  "invalid staged-media limit")

local preparedFormats = speaker.audioPreparedFormats()
assert(preparedFormats.mp3 == true and preparedFormats.wav == true,
  "prepared formats must be mp3+wav")

local supported = {}
for _, ext in pairs(speaker.speakSupportedFiles()) do supported[ext] = true end
assert(supported.mp3 and supported.wav and not supported.ogg,
  "compatibility finite formats must be mp3+wav only")

local streamFormats = speaker.getStreamFormats()
assert(type(streamFormats) == "table" and streamFormats.mp3,
  "MP3/ICY radio must be advertised")
assert(streamFormats.hls == nil and streamFormats.ts == nil,
  "retired HLS/TS formats must not be advertised")

local count = speaker.getSpeakerCount()
local discovered = speaker.getSpeakers()
assert(type(discovered) == "table" and #discovered == count,
  "getSpeakers/getSpeakerCount disagree")
for i = 1, count do
  local p = speaker.getSpeakerPos(i)
  assert(type(p) == "table" and type(p.x) == "number" and type(p.y) == "number" and type(p.z) == "number",
    "invalid speaker position at index " .. i)
end

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
    if status.state == "error" then error("finite playback error: " .. tostring(status.error)) end
    sleep(0.05)
  end
  error("timed out waiting for state " .. wanted)
end

local function waitMain(wanted, timeout)
  return waitForStatus(function() return speaker.audioStatus() end, wanted, timeout)
end

print("v10 core acceptance: " .. peripheralName)
print("attached speaker snapshot: " .. count)

assert(type(speaker.playNote("harp", 0.2, 1.0)) == "boolean", "playNote did not return boolean")
speaker.stop()
sleep(0.05)

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
assert(speaker.audioSetMuted(true), "audioSetMuted(true) failed")
assert(speaker.audioSetMuted(false), "audioSetMuted(false) failed")
assert(speaker.audioSetLooping(true), "audioSetLooping(true) failed")
assert(speaker.audioSetLooping(false), "audioSetLooping(false) failed")
speaker.audioStop()
waitMain("idle", 5)

if wav then
  assert(speaker.speakWav(wav, 0.35), "speakWav rejected valid test file")
  waitMain("playing", 15)
  speaker.audioStop()
  waitMain("idle", 5)
end

local pcm = {}
for i = 1, 2400 do pcm[i] = math.floor(math.sin(i * 0.08) * 24000) end
assert(speaker.speakPCM(pcm, 0.25), "speakPCM rejected signed-16 data")
sleep(0.1)
speaker.speakStop()

if count >= 2 then
  speaker.audioStopAll()
  assert(speaker.speakMp3All(mp3, 0.35), "speakMp3All rejected")

  local first = waitForStatus(function() return speaker.audioStatusAt(1) end, "playing", 15)
  local second = waitForStatus(function() return speaker.audioStatusAt(2) end, "playing", 15)
  if first.playbackId and second.playbackId then
    assert(first.playbackId == second.playbackId, "All endpoints did not share one playback")
  end

  assert(speaker.audioSetVolumeAt(1, 0.25), "audioSetVolumeAt failed")
  assert(speaker.audioSetMutedAt(2, true), "audioSetMutedAt(true) failed")
  assert(speaker.audioSetMutedAt(2, false), "audioSetMutedAt(false) failed")
  assert(speaker.audioSetVolumeAll(0.3), "audioSetVolumeAll failed")
  assert(speaker.audioSetMutedAll(true), "audioSetMutedAll(true) failed")
  assert(speaker.audioSetMutedAll(false), "audioSetMutedAll(false) failed")

  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 5)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) incorrectly stopped the surviving shared playback")

  speaker.audioStopAll()
  waitForStatus(function() return speaker.audioStatusAt(1) end, "idle", 5)

  assert(speaker.speakPCMAll(pcm, 0.2), "speakPCMAll rejected a small group chunk")
  sleep(0.15)
else
  print("multispeaker checks skipped: attach at least two speakers")
end

speaker.audioStop()
speaker.speakStop()
print("[PASS] frozen v10 automated core acceptance")
print("Manual gates remain: audibility/spatial sync, listener/recovery, Sable/VS2, radio sync, SPR.")
