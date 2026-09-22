-- Frozen protocol-v10 Phase 0 interactive runtime acceptance.
-- Usage: v10_phase0_acceptance <mp3> <wav>
--
-- Structure:
--   1) fast mostly-silent mechanical preflight
--   2) paced human audible gates with replay/pass/fail controls
--
-- Final PASS requires both the automated checks and every audible gate.

local args = {...}
assert(args[1] and args[2], "usage: v10_phase0_acceptance <mp3> <wav>")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local speakerName = assert(peripheral.getName(speaker), "could not resolve speaker peripheral name")
local monitor = peripheral.find("monitor")

local LOG_PATH = "/v10-phase0.log"
local startedMs = os.epoch("utc")
local state = {
  mode = "startup",
  phase = "initializing",
  detail = "",
  result = "RUNNING",
  autoPassed = 0,
  autoTotal = 6,
  manualPassed = 0,
  manualTotal = 5,
  speakerCount = 0,
  lastStatus = "-",
  lastEvent = "-",
  prompt = {},
}
local tail = {}

local function elapsed()
  return (os.epoch("utc") - startedMs) / 1000
end

local function shorten(value, limit)
  local s = tostring(value or "")
  if #s <= limit then return s end
  return s:sub(1, math.max(1, limit - 3)) .. "..."
end

local function appendTail(line)
  tail[#tail + 1] = line
  while #tail > 8 do table.remove(tail, 1) end
end

local function writeLog(kind, message)
  local line = ("[%7.2fs] %-7s %s"):format(elapsed(), kind, tostring(message))
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
    writeAt(monitor, 2, ("Mode: %s   Result: %s"):format(state.mode, state.result))
    writeAt(monitor, 3, ("Auto %d/%d | Listen %d/%d | Speakers %d")
      :format(state.autoPassed, state.autoTotal, state.manualPassed, state.manualTotal, state.speakerCount))
    writeAt(monitor, 4, "Test: " .. state.phase)
    writeAt(monitor, 5, "Now: " .. state.detail)

    local y = 6
    for i = 1, #state.prompt do
      if y > h then break end
      writeAt(monitor, y, state.prompt[i])
      y = y + 1
    end

    if y <= h then
      writeAt(monitor, y, "Log: " .. LOG_PATH)
      y = y + 1
    end

    if y <= h then
      writeAt(monitor, y, "Status: " .. shorten(state.lastStatus, math.max(12, w - 8)))
      y = y + 1
    end

    if y <= h then
      writeAt(monitor, y, "Event: " .. shorten(state.lastEvent, math.max(12, w - 7)))
    end
  end)

  if not ok then monitor = nil end
end

local function setDisplay(mode, phase, detail, prompt)
  state.mode = mode or state.mode
  state.phase = phase or state.phase
  state.detail = detail or ""
  state.prompt = prompt or {}
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
    writeLog("EVENT", state.lastEvent)
    render()
  end
end

local function timerSleep(seconds, detail)
  if detail then
    state.detail = detail
    render()
  end

  local timer = os.startTimer(seconds)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == "timer" and e[2] == timer then return end
  end
end

local function waitForEvent(name, timeout, detail)
  state.detail = detail or ("waiting for " .. name)
  render()

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
      writeLog("STATE", (label or "status") .. " -> " .. encoded)
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
  writeLog("ASSET", ("%s = %d bytes"):format(path, #data))
  return data
end

local function safeStop()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function dumpSnapshot(reason)
  writeLog("DUMP", "snapshot: " .. tostring(reason))

  local okMain, main = pcall(function() return speaker.audioStatus() end)
  writeLog("DUMP", "main status = " .. (okMain and serialize(main) or ("ERROR " .. tostring(main))))

  local okStream, streamActive = pcall(function() return speaker.isStreaming() end)
  local okUrl, streamUrl = pcall(function() return speaker.getStreamUrl() end)
  local okMeta, streamMeta = pcall(function() return speaker.getStreamMeta() end)
  writeLog("DUMP", "stream active = " .. (okStream and tostring(streamActive) or ("ERROR " .. tostring(streamActive))))
  writeLog("DUMP", "stream url = " .. (okUrl and serialize(streamUrl) or ("ERROR " .. tostring(streamUrl))))
  writeLog("DUMP", "stream meta = " .. (okMeta and serialize(streamMeta) or ("ERROR " .. tostring(streamMeta))))

  local okQueue, queueSize = pcall(function() return speaker.speakQueueSize() end)
  writeLog("DUMP", "RAW queue size = " .. (okQueue and tostring(queueSize) or ("ERROR " .. tostring(queueSize))))

  for i = 1, state.speakerCount do
    local okStatus, status = pcall(function() return speaker.audioStatusAt(i) end)
    local okPos, pos = pcall(function() return speaker.getSpeakerPos(i) end)
    writeLog("DUMP", ("endpoint %d status = %s"):format(
      i, okStatus and serialize(status) or ("ERROR " .. tostring(status))))
    writeLog("DUMP", ("endpoint %d pos = %s"):format(
      i, okPos and serialize(pos) or ("ERROR " .. tostring(pos))))
  end
end

local function failRun(where, err)
  state.result = "FAIL"
  state.detail = tostring(err)
  render()
  writeLog("FAIL", where .. ": " .. tostring(err))
  dumpSnapshot("failure in " .. where)
  safeStop()
  error(("PHASE 0 FAILED in %s\n%s\nDiagnostic log: %s"):format(where, tostring(err), LOG_PATH), 0)
end

local function autoCheck(name, fn)
  setDisplay("AUTO", name, "running mostly-silent mechanical check", {})
  writeLog("BEGIN", name)
  local ok, err = pcall(fn)

  if not ok then
    failRun(name, err)
  end

  state.autoPassed = state.autoPassed + 1
  writeLog("PASS", name)
  render()
end

local function waitEnter()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "key" and e[2] == keys.enter then return end
    noteEvent(e)
  end
end

local function getManualChoice()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end

    if e[1] == "char" then
      local c = string.lower(e[2])
      if c == "p" or c == "r" or c == "f" then return c end
    end

    noteEvent(e)
  end
end

local function manualGate(name, instructions, action)
  while true do
    safeStop()

    setDisplay("LISTEN", name, "ready - press ENTER to start", instructions)
    print("")
    print("=== " .. name .. " ===")
    for _, line in ipairs(instructions) do print(line) end
    print("Press ENTER when ready.")
    waitEnter()

    setDisplay("LISTEN", name, "PLAYING - listen now", instructions)
    writeLog("LISTEN", name .. " started")

    local ok, err = pcall(action)
    safeStop()

    if not ok then
      failRun(name, err)
    end

    state.detail = "P = pass   R = replay   F = fail"
    state.prompt = {
      "Judge only what you just heard.",
      "P = PASS",
      "R = REPLAY",
      "F = FAIL",
    }
    render()

    print("Result: [P]ass  [R]eplay  [F]ail")
    local choice = getManualChoice()

    if choice == "p" then
      state.manualPassed = state.manualPassed + 1
      writeLog("MANUAL", name .. " = PASS")
      state.prompt = {}
      render()
      return
    elseif choice == "r" then
      writeLog("MANUAL", name .. " = REPLAY")
    else
      writeLog("MANUAL", name .. " = FAIL (user-observed)")
      failRun(name, "user marked audible gate as failed")
    end
  end
end

local function makeRawChunk(samples, step, amplitude)
  local chunk = {}
  for i = 1, samples do
    chunk[i] = math.floor(math.sin(i * step) * amplitude)
  end
  return chunk
end

local function sendRawAll(chunk, volume, timeout)
  local deadline = os.startTimer(timeout)

  while true do
    if speaker.speakPCMAll(chunk, volume) then
      os.cancelTimer(deadline)
      return
    end

    while true do
      local e = {os.pullEventRaw()}
      if e[1] == "terminate" then error("terminated", 0) end
      noteEvent(e)

      if e[1] == "hqspeaker_audio_empty" then break end
      if e[1] == "timer" and e[2] == deadline then
        error("timed out waiting for RAW group capacity", 0)
      end
    end
  end
end

-- Fresh log.
do
  local h = fs.open(LOG_PATH, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 interactive Phase 0 diagnostics")
    h.writeLine("Started UTC ms: " .. tostring(startedMs))
    h.writeLine("Computer ID: " .. tostring(os.getComputerID()))
    local computerLabel = os.getComputerLabel()
    h.writeLine("Computer label: " .. tostring(computerLabel or "<none>"))
    h.writeLine("OS: " .. tostring(os.version()))
    h.writeLine("Speaker peripheral: " .. speakerName)
    h.close()
  end
end

local mp3 = readBinary(args[1])
local wav = readBinary(args[2])

local methods = {}
for _, name in ipairs(peripheral.getMethods(speakerName) or {}) do
  methods[name] = true
end

state.speakerCount = speaker.getSpeakerCount()
if state.speakerCount < 2 then
  state.result = "FAIL"
  state.detail = "need at least 2 attached speakers"
  render()
  error("interactive Phase 0 requires at least 2 attached speakers", 0)
end

writeLog("INFO", "speaker count = " .. state.speakerCount)
writeLog("INFO", "speakers = " .. serialize(speaker.getSpeakers()))
writeLog("INFO", "monitor = " .. (monitor and tostring(peripheral.getName(monitor)) or "none"))
render()

-- ---------------------------------------------------------------------------
-- Part 1: fast, mostly-silent mechanical preflight.
-- ---------------------------------------------------------------------------

autoCheck("A1/6 Frozen API + discovery", function()
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

  local prepared = speaker.audioPreparedFormats()
  assert(prepared.mp3 == true and prepared.wav == true, "prepared formats must be mp3+wav")

  local files = {}
  for _, ext in pairs(speaker.speakSupportedFiles()) do files[ext] = true end
  assert(files.mp3 and files.wav and not files.ogg, "finite formats must be mp3+wav only")

  local streams = speaker.getStreamFormats()
  assert(type(streams) == "table" and streams.mp3, "MP3/ICY radio must be advertised")
  assert(streams.hls == nil and streams.ts == nil, "retired HLS/TS must not be advertised")

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

autoCheck("A2/6 Native CC:T mechanics", function()
  local idleEvents = countEvents("speaker_audio_empty", 0.50)
  assert(idleEvents == 0, "idle speaker spammed speaker_audio_empty (" .. idleEvents .. " events)")

  local audio = {}
  for i = 1, 4800 do audio[i] = math.floor(math.sin(i * 0.08) * 100) end

  assert(speaker.playAudio(audio, 0.0) == true, "first silent playAudio buffer was rejected")
  assert(speaker.playAudio(audio, 0.0) == false, "second immediate playAudio should be backpressured")
  waitForEvent("speaker_audio_empty", 5, "waiting for native capacity event")
  assert(speaker.playAudio(audio, 0.0) == true, "playAudio retry was rejected")
  speaker.stop()
end)

autoCheck("A3/6 Finite lifecycle mechanics", function()
  speaker.audioStop()

  assert(speaker.speakMp3(mp3, 0.0), "speakMp3 rejected valid MP3")
  local playing = waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3")
  assert(type(playing.duration) == "number" and playing.duration > 1, "MP3 duration missing")

  assert(speaker.audioPause(), "audioPause failed")
  waitForStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3")
  assert(speaker.audioResume(), "audioResume failed")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3")
  assert(speaker.audioSeek(math.min(1.0, playing.duration * 0.25)), "audioSeek failed")
  assert(speaker.audioSetLooping(true), "audioSetLooping(true) failed")
  assert(speaker.audioSetLooping(false), "audioSetLooping(false) failed")
  assert(speaker.audioSeek(playing.duration), "seek-to-duration failed")
  waitForStatus(function() return speaker.audioStatus() end, "ended", 5, "MP3")
  assert(speaker.speakIsPlaying() == false, "speakIsPlaying stayed true after finite end")

  speaker.audioStop()
  waitForStatus(function() return speaker.audioStatus() end, "idle", 5, "MP3")

  assert(speaker.speakWav(wav, 0.0), "speakWav rejected valid WAV")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV")
  speaker.audioStop()
  waitForStatus(function() return speaker.audioStatus() end, "idle", 5, "WAV")
end)

autoCheck("A4/6 RAW backpressure mechanics", function()
  local chunk = makeRawChunk(24000, 0.055, 22000)
  speaker.speakStop()

  local accepted = 0
  local rejected = false
  for _ = 1, 20 do
    if speaker.speakPCM(chunk, 0.0) then
      accepted = accepted + 1
    else
      rejected = true
      break
    end
  end

  assert(accepted > 0, "RAW rejected the first chunk")
  assert(rejected, "RAW never exercised bounded backpressure")
  writeLog("INFO", "RAW chunks accepted before rejection = " .. accepted)

  waitForEvent("hqspeaker_audio_empty", 10, "waiting for HQ RAW capacity event")
  assert(speaker.speakPCM(chunk, 0.0), "RAW retry after hqspeaker_audio_empty was rejected")
  speaker.speakStop()
  timerSleep(0.10)
end)

autoCheck("A5/6 Multispeaker finite mechanics", function()
  speaker.audioStopAll()
  assert(speaker.speakMp3All(mp3, 0.0), "speakMp3All rejected")

  local first = waitForStatus(function() return speaker.audioStatusAt(1) end, "playing", 15, "finite endpoint 1")
  local playbackId = first.playbackId

  for i = 2, state.speakerCount do
    local s = waitForStatus(function() return speaker.audioStatusAt(i) end, "playing", 15,
      "finite endpoint " .. i)

    if playbackId and s.playbackId then
      assert(s.playbackId == playbackId, "shared playbackId mismatch at endpoint " .. i)
    end
  end

  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 5, "finite endpoint 2")

  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) incorrectly stopped endpoint 1")

  speaker.audioStopAll()
  for i = 1, state.speakerCount do
    waitForStatus(function() return speaker.audioStatusAt(i) end, "idle", 5, "finite endpoint " .. i)
  end
end)

autoCheck("A6/6 RAW All/At mechanics", function()
  local short = makeRawChunk(4800, 0.08, 18000)

  assert(speaker.speakPCMAll(short, 0.0), "speakPCMAll rejected")
  timerSleep(0.15)
  speaker.audioStopAll()

  assert(speaker.speakPCMAt(2, short, 0.0), "speakPCMAt(2) rejected")
  timerSleep(0.10)
  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 3, "RAW endpoint 2")
  speaker.audioStopAll()
end)

writeLog("PASS", "AUTOMATED MECHANICAL PREFLIGHT PASSED")
safeStop()

-- ---------------------------------------------------------------------------
-- Part 2: paced audible gates. The user's answers are part of acceptance.
-- ---------------------------------------------------------------------------

print("")
print("Mechanical preflight passed.")
print("Now the script will STOP before each audible test.")
print("Use P=pass, R=replay, F=fail after every test.")

manualGate("L1/5 Native CC:T audio", {
  "Expect: clear note sequence, then game sound,",
  "then a short raw CC:T tone.",
  "No crackle, missing sound, or weird overlap.",
}, function()
  local notes = {"harp", "harp", "harp", "harp"}
  local pitches = {0.75, 1.0, 1.25, 1.5}

  for i = 1, #notes do
    assert(speaker.playNote(notes[i], 1.0, pitches[i]), "playNote was rejected")
    timerSleep(0.65, ("native note %d/4"):format(i))
  end

  for i = 1, 3 do
    assert(speaker.playSound("minecraft:entity.experience_orb.pickup", 0.7, 0.8 + i * 0.15),
      "playSound was rejected")
    timerSleep(0.75, ("native game sound %d/3"):format(i))
  end

  local audio = makeRawChunk(12000, 0.08, 90)
  assert(speaker.playAudio(audio, 0.8), "native playAudio was rejected")
  waitForEvent("speaker_audio_empty", 5, "native playAudio tone")
  timerSleep(0.50, "native sequence complete")
end)

manualGate("L2/5 MP3 controls", {
  "Expect: MP3 tone for 4s, silence for 2s,",
  "then the SAME playback resumes for 4s.",
  "No restart, corruption, or unexpected sound.",
}, function()
  assert(speaker.speakMp3(mp3, 0.65), "speakMp3 rejected")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3 listen")
  timerSleep(4.0, "MP3 playing")

  assert(speaker.audioPause(), "audioPause failed")
  waitForStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3 listen")
  timerSleep(2.0, "MP3 should be SILENT")

  assert(speaker.audioResume(), "audioResume failed")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3 listen")
  timerSleep(4.0, "MP3 resumed")
end)

manualGate("L3/5 WAV playback", {
  "Expect: clean WAV test tone for 8 seconds.",
  "It should sound stable with no crackle/stutter.",
}, function()
  assert(speaker.speakWav(wav, 0.65), "speakWav rejected")
  waitForStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV listen")
  timerSleep(8.0, "WAV playing")
end)

manualGate("L4/5 Multispeaker sync + stopAt", {
  "Stand where you can hear BOTH speakers.",
  "First 8s: both play the same MP3 together.",
  "Then speaker #2 stops; #1 continues 6s.",
  "Fail for echo/drift, or if both stop together.",
}, function()
  writeLog("INFO", "speaker positions = " .. serialize(speaker.getSpeakers()))

  assert(speaker.speakMp3All(mp3, 0.60), "speakMp3All rejected")
  for i = 1, state.speakerCount do
    waitForStatus(function() return speaker.audioStatusAt(i) end, "playing", 15,
      "listen endpoint " .. i)
  end

  timerSleep(8.0, "BOTH speakers should be synchronized")

  speaker.audioStopAt(2)
  waitForStatus(function() return speaker.audioStatusAt(2) end, "idle", 5, "listen endpoint 2")
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "endpoint 1 did not survive audioStopAt(2)")

  timerSleep(6.0, "ONLY speaker #1 should still play")
end)

manualGate("L5/5 RAW multispeaker", {
  "Expect: about 6 seconds of continuous RAW tone",
  "from BOTH speakers together.",
  "Fail for gaps, obvious echo/desync, or crackle.",
}, function()
  local chunk = makeRawChunk(12000, 0.08, 15000)

  for i = 1, 24 do
    sendRawAll(chunk, 0.45, 5)
    state.detail = ("RAW chunk %d/24"):format(i)
    render()
  end

  timerSleep(0.75, "letting final RAW audio drain")
end)

dumpSnapshot("interactive Phase 0 complete")
safeStop()

state.result = "PASS"
state.mode = "DONE"
state.phase = "Phase 0 complete"
state.detail = "Automated + audible gates passed"
state.prompt = {
  "PHASE 0 PASSED",
  "Send /v10-phase0.log back for evidence.",
}
render()

writeLog("PASS", "ALL AUTOMATED AND AUDIBLE PHASE 0 GATES PASSED")
print("")
print("[PASS] v10 INTERACTIVE Phase 0")
print("Diagnostic log: " .. LOG_PATH)
