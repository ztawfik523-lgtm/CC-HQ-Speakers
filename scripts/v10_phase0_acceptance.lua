-- Frozen protocol-v10 Phase 0 runtime acceptance runner.
-- Usage: v10_phase0_acceptance <mp3> <wav>
--
-- Runs the automated Phase 0 checks in one place with explicit timer-based waits,
-- a readable monitor dashboard when a monitor is attached, and a persistent log.
-- Audibility is still a human observation: automated PASS does not prove that sound
-- was actually heard.

local args = {...}
assert(args[1] and args[2], "usage: v10_phase0_acceptance <mp3> <wav>")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local speakerName = assert(peripheral.getName(speaker), "could not resolve speaker peripheral name")
local monitor = peripheral.find("monitor")

local LOG_PATH = "/v10-phase0.log"
local startedMs = os.epoch("utc")
local state = {
  phase = "startup",
  detail = "initializing",
  result = "RUNNING",
  passed = 0,
  total = 6,
  speakerCount = 0,
  lastStatus = "-",
  lastEvent = "-",
}
local tail = {}

local function shorten(value, limit)
  local s = tostring(value or "")
  if #s <= limit then return s end
  return s:sub(1, math.max(1, limit - 3)) .. "..."
end

local function appendTail(line)
  tail[#tail + 1] = line
  while #tail > 8 do table.remove(tail, 1) end
end

local function elapsed()
  return (os.epoch("utc") - startedMs) / 1000
end

local function logLine(kind, message)
  local line = ("[%7.2fs] %-6s %s"):format(elapsed(), kind, tostring(message))
  print(line)
  appendTail(line)
  local h = fs.open(LOG_PATH, "a")
  if h then
    h.writeLine(line)
    h.close()
  end
end

local function writeAt(target, y, text)
  local w = select(1, target.getSize())
  target.setCursorPos(1, y)
  target.clearLine()
  target.write(shorten(text, w))
end

local function render()
  if not monitor then return end
  local ok = pcall(function()
    monitor.setTextScale(0.5)
    local w, h = monitor.getSize()
    monitor.setCursorBlink(false)
    monitor.clear()
    writeAt(monitor, 1, "CC:HQ Speakers - v10 Phase 0")
    writeAt(monitor, 2, ("Result: %s   Time: %.1fs"):format(state.result, elapsed()))
    writeAt(monitor, 3, ("Checks: %d/%d   Speakers: %d"):format(state.passed, state.total, state.speakerCount))
    writeAt(monitor, 4, "Phase: " .. state.phase)
    writeAt(monitor, 5, "Now: " .. state.detail)
    writeAt(monitor, 6, "Status: " .. shorten(state.lastStatus, math.max(12, w - 8)))
    writeAt(monitor, 7, "Event: " .. shorten(state.lastEvent, math.max(12, w - 7)))
    writeAt(monitor, 8, "Log: " .. LOG_PATH)
    if h >= 10 then
      writeAt(monitor, 9, string.rep("-", math.max(1, math.min(w, 40))))
      local first = math.max(1, #tail - (h - 10))
      local y = 10
      for i = first, #tail do
        if y > h then break end
        writeAt(monitor, y, tail[i])
        y = y + 1
      end
    end
  end)
  if not ok then monitor = nil end
end

local function setNow(phase, detail)
  state.phase = phase or state.phase
  state.detail = detail or state.detail
  render()
end

local function serialize(value)
  local ok, encoded = pcall(textutils.serialize, value, {compact = true})
  return ok and encoded or tostring(value)
end

local interesting = {
  speaker_audio_empty = true,
  hqspeaker_audio_empty = true,
  hqspeaker_audio_state = true,
  hqspeaker_metadata = true,
  peripheral = true,
  peripheral_detach = true,
}

local function noteEvent(e)
  if interesting[e[1]] then
    state.lastEvent = serialize(e)
    logLine("EVENT", state.lastEvent)
    render()
  end
end

local function timerSleep(seconds, detail)
  if detail then setNow(nil, detail) end
  local timer = os.startTimer(seconds)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == "timer" and e[2] == timer then return end
  end
end

local function waitForEvent(name, timeout, detail)
  setNow(nil, detail or ("waiting for " .. name))
  local timer = os.startTimer(timeout)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == name then
      os.cancelTimer(timer)
      return e
    end
    if e[1] == "timer" and e[2] == timer then
      error(("timed out after %.1fs waiting for %s"):format(timeout, name), 0)
    end
  end
end

local function countEvents(name, seconds)
  local timer = os.startTimer(seconds)
  local count = 0
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == name then count = count + 1 end
    if e[1] == "timer" and e[2] == timer then return count end
  end
end

local function waitForStatus(getStatus, wanted, timeout, label)
  local deadline = os.startTimer(timeout)
  local poll = os.startTimer(0.05)
  local lastEncoded = nil

  local function sample()
    local status = getStatus()
    local encoded = serialize(status)
    state.lastStatus = encoded
    if encoded ~= lastEncoded then
      logLine("STATE", (label or "status") .. " -> " .. encoded)
      lastEncoded = encoded
      render()
    end
    if status.state == "error" then
      error((label or "audio") .. " error: " .. tostring(status.error), 0)
    end
    return status
  end

  local first = sample()
  if first.state == wanted then
    os.cancelTimer(deadline)
    os.cancelTimer(poll)
    return first
  end

  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)

    if e[1] == "timer" and e[2] == deadline then
      error(("timed out after %.1fs waiting for %s -> %s; last=%s")
        :format(timeout, label or "status", wanted, state.lastStatus), 0)
    end

    if e[1] == "timer" and e[2] == poll then
      local status = sample()
      if status.state == wanted then
        os.cancelTimer(deadline)
        return status
      end
      poll = os.startTimer(0.05)
    end
  end
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  logLine("ASSET", ("%s = %d bytes"):format(path, #data))
  return data
end

local function safeStop()
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function check(name, fn)
  state.phase = name
  state.detail = "starting"
  render()
  logLine("BEGIN", name)
  local ok, err = pcall(fn)
  if not ok then
    state.result = "FAIL"
    state.detail = tostring(err)
    logLine("FAIL", name .. ": " .. tostring(err))
    render()
    safeStop()
    error(("PHASE 0 FAILED in %s\n%s\nDiagnostic log: %s"):format(name, tostring(err), LOG_PATH), 0)
  end
  state.passed = state.passed + 1
  state.detail = "passed"
  logLine("PASS", name)
  render()
end

-- Start a fresh log each run.
do
  local h = fs.open(LOG_PATH, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 Phase 0 runtime diagnostics")
    h.writeLine("Started UTC ms: " .. tostring(startedMs))
    h.writeLine("Computer ID: " .. tostring(os.getComputerID()))
    h.writeLine("Computer label: " .. tostring(os.getComputerLabel()))
    h.writeLine("OS: " .. tostring(os.version()))
    h.writeLine("Speaker peripheral: " .. speakerName)
    h.close()
  end
end

local mp3 = readBinary(args[1])
local wav = readBinary(args[2])

local methods = {}
for _, name in ipairs(peripheral.getMethods(speakerName) or {}) do methods[name] = true end

state.speakerCount = speaker.getSpeakerCount()
assert(state.speakerCount >= 2, "combined Phase 0 requires at least 2 attached speakers; use component scripts for single-speaker diagnosis")
logLine("INFO", "speaker count = " .. state.speakerCount)
logLine("INFO", "speakers = " .. serialize(speaker.getSpeakers()))
logLine("INFO", "monitor = " .. (monitor and tostring(peripheral.getName(monitor)) or "none"))
render()

check("1/6 Frozen API + discovery", function()
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
    "getStreamUrl", "getStreamFormats", "getStreamMeta", "getStreamTitle", "getStreamArtist",
    "getStreamSong", "getStreamStation", "getStreamGenre", "getStreamMetaSerial",
    "getPeripheralType", "getPos", "getSpeakerCount", "getSpeakers", "getSpeakerPos",
  }
  for _, name in ipairs(required) do
    assert(methods[name], "missing frozen API method: " .. name)
  end

  for _, name in ipairs({
    "speakOgg", "speakAudio", "speakFile", "speakPacked",
    "speakStopAll", "speakStopAt", "speakVolumeAll", "setLooping", "setLoopingAll",
    "speakHLS", "speakHLSAll", "speakHLSAt", "speakTS", "speakTSAll", "speakTSAt",
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
  assert(type(streamFormats) == "table" and streamFormats.mp3, "MP3/ICY radio must be advertised")
  assert(streamFormats.hls == nil and streamFormats.ts == nil, "retired HLS/TS must not be advertised")

  local discovered = speaker.getSpeakers()
  assert(type(discovered) == "table" and #discovered == state.speakerCount,
    "getSpeakers/getSpeakerCount disagree")
  for i = 1, state.speakerCount do
    local p = speaker.getSpeakerPos(i)
    assert(type(p) == "table" and type(p.x) == "number"
      and type(p.y) == "number" and type(p.z) == "number",
      "invalid speaker position at index " .. i)
  end
end)

check("2/6 Native CC:T contract", function()
  setNow(nil, "checking idle speaker_audio_empty")
  local idleEvents = countEvents("speaker_audio_empty", 0.50)
  assert(idleEvents == 0, "idle speaker spammed speaker_audio_empty (" .. idleEvents .. " events)")

  setNow(nil, "playing native note + sound; listen")
  assert(speaker.playNote("harp", 1.0, 1.0) == true, "playNote was rejected")
  timerSleep(0.20)
  assert(speaker.playSound("minecraft:entity.experience_orb.pickup", 0.5, 1.0) == true,
    "playSound was rejected")
  timerSleep(0.30)
  speaker.stop()
  timerSleep(0.05)

  local audio = {}
  for i = 1, 4800 do audio[i] = math.floor(math.sin(i * 0.08) * 100) end
  assert(speaker.playAudio(audio, 0.35) == true, "first playAudio buffer was rejected")
  assert(speaker.playAudio(audio) == false, "second immediate playAudio should be backpressured")
  waitForEvent("speaker_audio_empty", 5, "waiting for native backpressure release")
  assert(speaker.playAudio(audio) == true, "playAudio retry after speaker_audio_empty was rejected")
  speaker.stop()
end)

check("3/6 Finite MP3/WAV lifecycle", function()
  speaker.audioStop()
  setNow(nil, "starting MP3")
  assert(speaker.speakMp3(mp3, 0.40), "speakMp3 rejected valid MP3")
  local playing = waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3")
  assert(type(playing.duration) == "number" and playing.duration > 1,
    "test MP3 must be longer than 1 second")

  assert(speaker.audioPause(), "audioPause failed")
  waitForStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3")
  assert(speaker.audioResume(), "audioResume failed")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3")

  local seekTarget = math.min(1.0, math.max(0, playing.duration * 0.25))
  assert(speaker.audioSeek(seekTarget), "audioSeek failed")
  assert(speaker.audioSetVolume(0.35), "audioSetVolume failed")
  assert(speaker.audioSetMuted(true), "audioSetMuted(true) failed")
  assert(speaker.audioSetMuted(false), "audioSetMuted(false) failed")
  assert(speaker.audioSetLooping(true), "audioSetLooping(true) failed")

  local nearEnd = math.max(0, playing.duration - 0.35)
  assert(speaker.audioSeek(nearEnd), "seek near end failed")
  local previous = nearEnd
  local wrapped = false
  local wrapDeadline = os.startTimer(5)
  local poll = os.startTimer(0.03)
  while not wrapped do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == "timer" and e[2] == wrapDeadline then error("did not observe finite loop wrap", 0) end
    if e[1] == "timer" and e[2] == poll then
      local s = speaker.audioStatus()
      state.lastStatus = serialize(s)
      assert(s.state == "playing", "loop stopped before wrap")
      if s.position + 0.15 < previous then
        wrapped = true
      else
        previous = s.position
        poll = os.startTimer(0.03)
      end
      render()
    end
  end
  os.cancelTimer(wrapDeadline)

  local beforeDisable = speaker.audioStatus().position
  assert(speaker.audioSetLooping(false), "failed to disable loop")
  timerSleep(0.05)
  local afterDisable = speaker.audioStatus()
  assert(afterDisable.state == "playing", "disabling loop changed playback state")
  assert(afterDisable.position < afterDisable.duration - 0.05, "disabling loop jumped to duration")
  assert(math.abs(afterDisable.position - beforeDisable) < 0.5, "disabling loop lost wrapped position")

  assert(speaker.audioSeek(afterDisable.duration), "seek exactly to duration failed")
  local ended = waitForStatus(function() return speaker.audioStatus() end, "ended", 5, "MP3")
  assert(math.abs(ended.position - ended.duration) < 0.1, "ended position was not duration")
  assert(speaker.speakIsPlaying() == false, "speakIsPlaying stayed true after finite end")
  speaker.audioStop()
  waitForStatus(function() return speaker.audioStatus() end, "idle", 5, "MP3")

  setNow(nil, "starting WAV")
  assert(speaker.speakWav(wav, 0.35), "speakWav rejected valid WAV")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV")
  timerSleep(0.30, "WAV playing; listen")
  speaker.audioStop()
  waitForStatus(function() return speaker.audioStatus() end, "idle", 5, "WAV")
end)

check("4/6 RAW backpressure", function()
  assert(speaker.speakSampleRate() == 48000, "RAW sample rate changed")
  assert(speaker.speakMaxSamples() == 131072, "RAW max sample count changed")
  local chunk = {}
  for i = 1, 24000 do chunk[i] = math.floor(math.sin(i * 0.055) * 22000) end

  speaker.speakStop()
  local accepted, rejected = 0, false
  for i = 1, 20 do
    if speaker.speakPCM(chunk, 0.20) then accepted = accepted + 1 else rejected = true break end
  end
  assert(accepted > 0, "RAW rejected the first chunk")
  assert(rejected, "RAW never exercised bounded backpressure")
  logLine("INFO", "RAW chunks accepted before rejection = " .. accepted)

  waitForEvent("hqspeaker_audio_empty", 10, "waiting for HQ RAW capacity event")
  assert(speaker.speakPCM(chunk, 0.20), "RAW retry after hqspeaker_audio_empty was rejected")
  speaker.speakStop()
  timerSleep(0.10)
end)

check("5/6 Multispeaker finite controls", function()
  speaker.audioStopAll()
  assert(speaker.speakMp3All(mp3, 0.35), "speakMp3All rejected")
  local first = waitForStatus(function() return speaker.audioStatusAt(1) end, "playing", 15, "finite endpoint 1")
  local playbackId = first.playbackId
  for i = 2, state.speakerCount do
    local s = waitForStatus(function() return speaker.audioStatusAt(i) end, "playing", 15,
      "finite endpoint " .. i)
    if playbackId and s.playbackId then
      assert(s.playbackId == playbackId, "shared playbackId mismatch at endpoint " .. i)
    end
  end

  assert(speaker.audioSetVolumeAt(1, 0.25), "audioSetVolumeAt(1) failed")
  assert(speaker.audioSetMutedAt(2, true), "audioSetMutedAt(2,true) failed")
  assert(speaker.audioSetMutedAt(2, false), "audioSetMutedAt(2,false) failed")
  assert(speaker.audioSetVolumeAll(0.30), "audioSetVolumeAll failed")
  assert(speaker.audioSetMutedAll(true), "audioSetMutedAll(true) failed")
  assert(speaker.audioSetMutedAll(false), "audioSetMutedAll(false) failed")

  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 5, "finite endpoint 2")
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) incorrectly stopped the surviving finite endpoint")

  speaker.audioStopAll()
  for i = 1, state.speakerCount do
    waitForStatus(function() return speaker.audioStatusAt(i) end, "idle", 5, "finite endpoint " .. i)
  end
end)

check("6/6 Multispeaker RAW All/At", function()
  local short = {}
  for i = 1, 4800 do short[i] = math.floor(math.sin(i * 0.08) * 18000) end

  assert(speaker.speakPCMAll(short, 0.18), "speakPCMAll rejected")
  timerSleep(0.25, "RAW All playing; listen for simultaneous start")
  speaker.speakStop()
  timerSleep(0.10)

  assert(speaker.speakPCMAt(2, short, 0.18), "speakPCMAt(2) rejected")
  timerSleep(0.15, "RAW At(2) playing")
  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 3, "RAW endpoint 2")
  speaker.speakStop()
end)

safeStop()
state.result = "AUTO PASS"
state.phase = "complete"
state.detail = "Automated Phase 0 passed. Confirm audible/manual observations separately."
render()
logLine("PASS", "ALL AUTOMATED PHASE 0 CHECKS PASSED")
logLine("NOTE", "Automated PASS does not prove audibility, spatial sync, movement, reload recovery, radio sync, or SPR.")
print("")
print("[PASS] v10 automated Phase 0")
print("Diagnostic log: " .. LOG_PATH)
print("Manual listening/runtime gates still remain.")
