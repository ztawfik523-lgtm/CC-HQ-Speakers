-- CC:HQ Speakers v10 master interactive runtime acceptance.
-- Usage:
--   v10_acceptance <mp3> <wav> [radio-url]
--
-- One file, one command, one monitor dashboard, one log.
-- Required core checks run automatically. Human-audible checks pause for a verdict.
-- Optional environment checks (moving platform, Sound Physics, radio) may be skipped.
--
-- Keys:
--   ENTER = start a listening/manual check
--   P     = pass
--   R     = replay
--   F     = fail
--   S     = skip (optional checks only)

local args = {...}
assert(args[1] and args[2], "usage: v10_acceptance <mp3> <wav> [radio-url]")

local MP3_PATH = args[1]
local WAV_PATH = args[2]
local RADIO_URL = args[3]

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local speakerName = assert(peripheral.getName(speaker), "could not resolve speaker peripheral name")
local monitor = peripheral.find("monitor")

local LOG = "/v10-acceptance.log"
local startedMs = os.epoch("utc")
local speakerCount = speaker.getSpeakerCount()
assert(speakerCount >= 2, "master acceptance requires at least 2 attached speakers")

local state = {
  mode = "START",
  test = "initializing",
  detail = "",
  result = "RUNNING",
  autoPassed = 0,
  autoTotal = 14,
  manualPassed = 0,
  manualTotal = 8,
  optionalPassed = 0,
  optionalSkipped = 0,
  lastStatus = "-",
  lastEvent = "-",
  prompt = {},
}
local tail = {}

local interesting = {
  speaker_audio_empty = true,
  hqspeaker_audio_empty = true,
  hqspeaker_audio_state = true,
  hqspeaker_metadata = true,
  peripheral = true,
  peripheral_detach = true,
}

local function elapsed()
  return (os.epoch("utc") - startedMs) / 1000
end

local function serialize(value)
  local ok, encoded = pcall(textutils.serialize, value, {compact = true})
  return ok and encoded or tostring(value)
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

local function log(kind, message)
  local line = ("[%7.2fs] %-8s %s"):format(elapsed(), kind, tostring(message))
  print(line)
  appendTail(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
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

    writeAt(monitor, 1, "CC:HQ Speakers - v10 MASTER")
    writeAt(monitor, 2, ("Mode %s | %s"):format(state.mode, state.result))
    writeAt(monitor, 3, ("Auto %d/%d | Listen %d/%d | Speakers %d")
      :format(state.autoPassed, state.autoTotal, state.manualPassed, state.manualTotal, speakerCount))
    writeAt(monitor, 4, ("Optional pass %d | skip %d"):format(state.optionalPassed, state.optionalSkipped))
    writeAt(monitor, 5, "Test: " .. state.test)
    writeAt(monitor, 6, "Now: " .. state.detail)

    local y = 7
    for _, line in ipairs(state.prompt) do
      if y > h then break end
      writeAt(monitor, y, line)
      y = y + 1
    end

    if y <= h then
      writeAt(monitor, y, "Status: " .. shorten(state.lastStatus, math.max(12, w - 8)))
      y = y + 1
    end
    if y <= h then
      writeAt(monitor, y, "Event: " .. shorten(state.lastEvent, math.max(12, w - 7)))
      y = y + 1
    end
    if y <= h then writeAt(monitor, y, "Log: " .. LOG) end

    if h >= 14 then
      local first = math.max(1, #tail - (h - 14))
      local row = 14
      for i = first, #tail do
        if row > h then break end
        writeAt(monitor, row, tail[i])
        row = row + 1
      end
    end
  end)
  if not ok then monitor = nil end
end

local function display(mode, test, detail, prompt)
  state.mode = mode or state.mode
  state.test = test or state.test
  state.detail = detail or ""
  state.prompt = prompt or {}
  render()
end

local function noteEvent(e)
  if interesting[e[1]] then
    state.lastEvent = serialize(e)
    log("EVENT", state.lastEvent)
    render()
  end
end

local function waitTimer(seconds, detail)
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

local function waitEvent(name, timeout, detail)
  state.detail = detail or ("waiting for " .. name)
  render()
  local deadline = os.startTimer(timeout)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == name then
      os.cancelTimer(deadline)
      return e
    end
    if e[1] == "timer" and e[2] == deadline then
      error(("timed out after %.1fs waiting for %s"):format(timeout, name), 0)
    end
  end
end

local function waitStatus(getStatus, wanted, timeout, label)
  local deadline = os.startTimer(timeout)
  local poll = os.startTimer(0.05)
  local last = nil

  local function sample()
    local status = getStatus()
    local encoded = serialize(status)
    state.lastStatus = encoded
    if encoded ~= last then
      log("STATE", (label or "status") .. " -> " .. encoded)
      last = encoded
      render()
    end
    if status.state == "error" then
      error((label or "audio") .. " error: " .. tostring(status.error), 0)
    end
    return status
  end

  local status = sample()
  if status.state == wanted then
    os.cancelTimer(deadline)
    os.cancelTimer(poll)
    return status
  end

  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == "timer" and e[2] == deadline then
      error(("timeout waiting for %s -> %s; last=%s"):format(label or "status", wanted, state.lastStatus), 0)
    end
    if e[1] == "timer" and e[2] == poll then
      status = sample()
      if status.state == wanted then
        os.cancelTimer(deadline)
        return status
      end
      poll = os.startTimer(0.05)
    end
  end
end

local function waitAt(index, wanted, timeout, label)
  return waitStatus(function() return speaker.audioStatusAt(index) end, wanted, timeout,
    label or ("endpoint " .. index))
end

local function safeStop()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  log("ASSET", ("%s = %d bytes"):format(path, #data))
  return data
end

local function dumpSnapshot(reason)
  log("DUMP", "snapshot: " .. tostring(reason))

  local okMain, main = pcall(function() return speaker.audioStatus() end)
  log("DUMP", "main = " .. (okMain and serialize(main) or ("ERROR " .. tostring(main))))

  local okQueue, queue = pcall(function() return speaker.speakQueueSize() end)
  log("DUMP", "RAW queue = " .. (okQueue and tostring(queue) or ("ERROR " .. tostring(queue))))

  local okStream, active = pcall(function() return speaker.isStreaming() end)
  local okUrl, url = pcall(function() return speaker.getStreamUrl() end)
  local okMeta, meta = pcall(function() return speaker.getStreamMeta() end)
  log("DUMP", "stream active = " .. (okStream and tostring(active) or ("ERROR " .. tostring(active))))
  log("DUMP", "stream url = " .. (okUrl and serialize(url) or ("ERROR " .. tostring(url))))
  log("DUMP", "stream meta = " .. (okMeta and serialize(meta) or ("ERROR " .. tostring(meta))))

  for i = 1, speaker.getSpeakerCount() do
    local okS, s = pcall(function() return speaker.audioStatusAt(i) end)
    local okP, p = pcall(function() return speaker.getSpeakerPos(i) end)
    log("DUMP", ("endpoint %d = %s"):format(i, okS and serialize(s) or ("ERROR " .. tostring(s))))
    log("DUMP", ("position %d = %s"):format(i, okP and serialize(p) or ("ERROR " .. tostring(p))))
  end
end

local function fail(test, err)
  state.result = "FAIL"
  state.detail = tostring(err)
  render()
  log("FAIL", test .. ": " .. tostring(err))
  dumpSnapshot("failure in " .. test)
  safeStop()
  error(("MASTER ACCEPTANCE FAILED in %s\n%s\nLog: %s"):format(test, tostring(err), LOG), 0)
end

local function auto(name, fn)
  display("AUTO", name, "running mechanical check", {})
  log("BEGIN", name)
  local ok, err = pcall(fn)
  if not ok then fail(name, err) end
  state.autoPassed = state.autoPassed + 1
  log("PASS", name)
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

local function choice(allowSkip)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "char" then
      local c = string.lower(e[2])
      if c == "p" or c == "r" or c == "f" or (allowSkip and c == "s") then return c end
    end
    noteEvent(e)
  end
end

local function gate(name, instructions, action, optional)
  while true do
    safeStop()
    display(optional and "OPTION" or "LISTEN", name, "press ENTER when ready", instructions)
    print("")
    print("=== " .. name .. " ===")
    for _, line in ipairs(instructions) do print(line) end
    if optional then print("Press ENTER to run, or S now to skip.") else print("Press ENTER when ready.") end

    if optional then
      while true do
        local e = {os.pullEventRaw()}
        if e[1] == "terminate" then error("terminated", 0) end
        if e[1] == "char" and string.lower(e[2]) == "s" then
          state.optionalSkipped = state.optionalSkipped + 1
          log("SKIP", name)
          state.prompt = {}
          render()
          return "skip"
        end
        if e[1] == "key" and e[2] == keys.enter then break end
        noteEvent(e)
      end
    else
      waitEnter()
    end

    state.detail = "RUNNING - listen/perform action now"
    render()
    log("MANUAL", name .. " started")

    local ok, err = pcall(action)
    safeStop()
    if not ok then fail(name, err) end

    state.detail = optional and "P=pass R=replay F=fail S=skip" or "P=pass R=replay F=fail"
    state.prompt = optional
      and {"P = PASS", "R = REPLAY", "F = FAIL", "S = SKIP"}
      or {"P = PASS", "R = REPLAY", "F = FAIL"}
    render()

    print(optional and "Result: [P]ass [R]eplay [F]ail [S]kip" or "Result: [P]ass [R]eplay [F]ail")
    local c = choice(optional)

    if c == "p" then
      if optional then
        state.optionalPassed = state.optionalPassed + 1
      else
        state.manualPassed = state.manualPassed + 1
      end
      log("MANUAL", name .. " = PASS")
      state.prompt = {}
      render()
      return "pass"
    elseif c == "r" then
      log("MANUAL", name .. " = REPLAY")
    elseif c == "s" then
      state.optionalSkipped = state.optionalSkipped + 1
      log("SKIP", name .. " after run")
      state.prompt = {}
      render()
      return "skip"
    else
      fail(name, "user marked manual check as failed")
    end
  end
end

local function makeRawChunk(samples, frequency, amplitude)
  local chunk = {}
  local step = 2 * math.pi * frequency / 48000
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
        error("timed out waiting for RAW capacity", 0)
      end
    end
  end
end

local function verifySharedPlaying()
  local first = waitAt(1, "playing", 15, "group endpoint 1")
  local id = first.playbackId
  for i = 2, speaker.getSpeakerCount() do
    local s = waitAt(i, "playing", 15, "group endpoint " .. i)
    if id and s.playbackId then
      assert(s.playbackId == id, "playbackId mismatch at endpoint " .. i)
    end
  end
  return first
end

local function samePos(a, b)
  return math.abs((a.x or 0) - (b.x or 0)) < 0.01
     and math.abs((a.y or 0) - (b.y or 0)) < 0.01
     and math.abs((a.z or 0) - (b.z or 0)) < 0.01
end

-- Fresh diagnostics.
do
  local h = fs.open(LOG, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 master interactive acceptance")
    h.writeLine("Started UTC ms: " .. tostring(startedMs))
    h.writeLine("Computer ID: " .. tostring(os.getComputerID()))
    h.writeLine("Computer label: " .. tostring(os.getComputerLabel() or "<none>"))
    h.writeLine("OS: " .. tostring(os.version()))
    h.writeLine("Speaker peripheral: " .. speakerName)
    h.writeLine("Speaker count: " .. tostring(speakerCount))
    h.writeLine("Speakers: " .. serialize(speaker.getSpeakers()))
    h.writeLine("Radio URL: " .. tostring(RADIO_URL or "<not supplied>"))
    h.close()
  end
end

local mp3 = readBinary(MP3_PATH)
local wav = readBinary(WAV_PATH)

local methods = {}
for _, name in ipairs(peripheral.getMethods(speakerName) or {}) do methods[name] = true end

-- ============================================================================
-- REQUIRED AUTOMATED CORE
-- ============================================================================

auto("A1/14 Frozen API surface", function()
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
  for _, name in ipairs(required) do assert(methods[name], "missing API method: " .. name) end

  for _, name in ipairs({
    "speakOgg", "speakAudio", "speakFile", "speakPacked",
    "speakStopAll", "speakStopAt", "speakVolumeAll", "setLooping", "setLoopingAll",
    "speakHLS", "speakHLSAll", "speakHLSAt", "speakTS", "speakTSAll", "speakTSAt",
  }) do
    assert(not methods[name], "retired API unexpectedly exposed: " .. name)
  end
end)

auto("A2/14 Discovery + limits", function()
  assert(speaker.getPeripheralType() == "speaker", "peripheral type changed")
  assert(speaker.getSpeakerCount() == speakerCount, "speaker count changed during startup")
  assert(speaker.speakSampleRate() == 48000, "RAW sample rate changed")
  assert(speaker.speakMaxSamples() == 131072, "RAW max samples changed")

  local prepared = speaker.audioPreparedFormats()
  assert(prepared.mp3 and prepared.wav, "prepared MP3/WAV formats missing")

  local files = {}
  for _, ext in pairs(speaker.speakSupportedFiles()) do files[ext] = true end
  assert(files.mp3 and files.wav and not files.ogg, "finite format set changed")

  local streams = speaker.getStreamFormats()
  assert(streams.mp3 and streams.hls == nil and streams.ts == nil, "stream format set changed")

  for i = 1, speakerCount do
    local p = speaker.getSpeakerPos(i)
    assert(type(p) == "table" and type(p.x) == "number"
      and type(p.y) == "number" and type(p.z) == "number", "bad position at endpoint " .. i)
  end
end)

auto("A3/14 Native backpressure", function()
  local audio = {}
  for i = 1, 4800 do audio[i] = math.floor(math.sin(i * 0.08) * 100) end
  assert(speaker.playAudio(audio, 0.0), "first silent native buffer rejected")
  assert(speaker.playAudio(audio, 0.0) == false, "native second buffer should backpressure")
  waitEvent("speaker_audio_empty", 5, "waiting for native capacity")
  assert(speaker.playAudio(audio, 0.0), "native retry rejected")
  speaker.stop()
end)

auto("A4/14 MP3 lifecycle", function()
  assert(speaker.speakMp3(mp3, 0.0), "MP3 rejected")
  local s = waitStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3")
  assert(type(s.duration) == "number" and s.duration > 1, "MP3 duration missing")
  assert(speaker.audioPause(), "pause failed")
  waitStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3")
  assert(speaker.audioResume(), "resume failed")
  waitStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3")
  assert(speaker.audioSeek(math.min(1.0, s.duration * 0.25)), "seek failed")
  assert(speaker.audioSetLooping(true), "loop enable failed")
  assert(speaker.audioSetLooping(false), "loop disable failed")
  assert(speaker.audioSeek(s.duration), "seek-to-end failed")
  waitStatus(function() return speaker.audioStatus() end, "ended", 5, "MP3")
  speaker.audioStop()
end)

auto("A5/14 WAV lifecycle", function()
  assert(speaker.speakWav(wav, 0.0), "WAV rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV")
  speaker.audioStop()
  waitStatus(function() return speaker.audioStatus() end, "idle", 5, "WAV")
end)

auto("A6/14 RAW backpressure", function()
  local chunk = makeRawChunk(24000, 420, 22000)
  local accepted, rejected = 0, false
  for _ = 1, 20 do
    if speaker.speakPCM(chunk, 0.0) then accepted = accepted + 1 else rejected = true break end
  end
  assert(accepted > 0, "RAW rejected first chunk")
  assert(rejected, "RAW never hit bounded backpressure")
  log("INFO", "RAW accepted before rejection = " .. accepted)
  waitEvent("hqspeaker_audio_empty", 10, "waiting for HQ RAW capacity")
  assert(speaker.speakPCM(chunk, 0.0), "RAW retry rejected")
  speaker.speakStop()
end)

auto("A7/14 Group finite authority", function()
  assert(speaker.speakMp3All(mp3, 0.0), "speakMp3All rejected")
  verifySharedPlaying()
  speaker.audioStopAll()
  for i = 1, speakerCount do waitAt(i, "idle", 5) end
end)

auto("A8/14 Endpoint-local stop", function()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected")
  verifySharedPlaying()
  speaker.audioStopAt(2)
  waitAt(2, "idle", 5)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) stopped endpoint 1")
  speaker.audioStopAll()
end)

auto("A9/14 Endpoint volume/mute", function()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected")
  verifySharedPlaying()
  assert(speaker.audioSetVolumeAt(1, 0.25), "endpoint volume failed")
  assert(speaker.audioSetMutedAt(2, true), "endpoint mute failed")
  assert(speaker.audioSetMutedAt(2, false), "endpoint unmute failed")
  assert(speaker.audioSetVolumeAll(0.30), "all volume failed")
  speaker.audioStopAll()
end)

auto("A10/14 RAW All/At", function()
  local short = makeRawChunk(4800, 500, 18000)
  assert(speaker.speakPCMAll(short, 0.0), "speakPCMAll rejected")
  waitTimer(0.15)
  speaker.audioStopAll()
  assert(speaker.speakPCMAt(2, short, 0.0), "speakPCMAt(2) rejected")
  waitTimer(0.10)
  speaker.audioStopAt(2)
  waitAt(2, "idle", 3)
  speaker.audioStopAll()
end)

auto("A11/14 Control race stress", function()
  for cycle = 1, 8 do
    assert(speaker.speakMp3All(mp3, 0.0), "stress group start rejected at cycle " .. cycle)
    local status = verifySharedPlaying()
    local seek = math.min(0.5, math.max(0, (status.duration or 1) * 0.1))

    parallel.waitForAll(
      function() speaker.audioSeek(seek) end,
      function() speaker.audioSetVolumeAt(1, 0.0) end,
      function() speaker.audioSetMutedAt(2, cycle % 2 == 0) end,
      function() speaker.audioSetLooping(cycle % 2 == 0) end
    )

    assert(speaker.audioSetLooping(false), "stress loop stabilization failed")
    assert(speaker.audioSetMutedAt(2, false), "stress unmute failed")
    assert(speaker.audioSetVolumeAt(1, 0.0), "stress volume stabilization failed")

    speaker.audioStopAll()
    for i = 1, speakerCount do waitAt(i, "idle", 5) end
  end
end)

auto("A12/14 Repeated group starts", function()
  for cycle = 1, 4 do
    assert(speaker.speakMp3All(mp3, 0.0), "repeat start rejected " .. cycle)
    verifySharedPlaying()
    speaker.audioStopAll()
    for i = 1, speakerCount do waitAt(i, "idle", 5) end
  end
end)

auto("A13/14 Stream API state", function()
  assert(not speaker.isStreaming(), "stream unexpectedly active before radio test")
  local meta = speaker.getStreamMeta()
  assert(type(meta) == "table", "stream metadata not a table")
  assert(type(speaker.getStreamTitle()) == "string", "stream title getter changed")
  assert(type(speaker.getStreamArtist()) == "string", "stream artist getter changed")
end)

auto("A14/14 Final idle cleanup", function()
  safeStop()
  for i = 1, speakerCount do waitAt(i, "idle", 5) end
end)

log("PASS", "ALL AUTOMATED CORE CHECKS PASSED")

-- ============================================================================
-- REQUIRED AUDIBLE CORE
-- ============================================================================

gate("L1/8 Native CC:T audio", {
  "Expect: four notes, three pickup sounds,",
  "then a short raw CC:T tone.",
  "No missing sounds, crackle, or weird overlap.",
}, function()
  local pitches = {6, 10, 14, 18}
  for i = 1, #pitches do
    assert(speaker.playNote("harp", 1.0, pitches[i]), "playNote rejected")
    waitTimer(0.65, "native note " .. i .. "/4")
  end
  for i = 1, 3 do
    assert(speaker.playSound("minecraft:entity.experience_orb.pickup", 0.7, 0.8 + i * 0.15),
      "playSound rejected")
    waitTimer(0.75, "native game sound " .. i .. "/3")
  end
  local audio = {}
  for i = 1, 12000 do audio[i] = math.floor(math.sin(i * 0.08) * 90) end
  assert(speaker.playAudio(audio, 0.8), "native playAudio rejected")
  waitEvent("speaker_audio_empty", 5, "native raw tone")
end, false)

gate("L2/8 MP3 pause/resume", {
  "MP3 plays 4 sec, SILENCE for 2 sec,",
  "then the same playback resumes for 4 sec.",
  "No restart, corruption, or unexpected sound.",
}, function()
  assert(speaker.speakMp3(mp3, 0.65), "MP3 rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3 listen")
  waitTimer(4.0, "MP3 playing")
  assert(speaker.audioPause(), "pause failed")
  waitStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3 listen")
  waitTimer(2.0, "MP3 should be SILENT")
  assert(speaker.audioResume(), "resume failed")
  waitStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3 listen")
  waitTimer(4.0, "MP3 resumed")
end, false)

gate("L3/8 WAV playback", {
  "Expect clean WAV playback for 8 seconds.",
  "No crackle, stutter, pitch change, or dropout.",
}, function()
  assert(speaker.speakWav(wav, 0.65), "WAV rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV listen")
  waitTimer(8.0, "WAV playing")
end, false)

gate("L4/8 Group MP3 sync", {
  "Stand roughly equally far from both speakers.",
  "Both play the same MP3 for 12 seconds.",
  "It should sound like one synchronized playback.",
  "Fail for echo, drift, staggered start, or dropout.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.60), "group MP3 rejected")
  verifySharedPlaying()
  waitTimer(12.0, "all speakers synchronized")
end, false)

local p2 = speaker.getSpeakerPos(2)
gate("L5/8 Endpoint-local stop", {
  "Both speakers play together for 6 seconds.",
  ("Then #2 at %.0f,%.0f,%.0f stops."):format(p2.x, p2.y, p2.z),
  "Speaker #1 must keep playing for another 6 sec.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.60), "group MP3 rejected")
  verifySharedPlaying()
  waitTimer(6.0, "both speakers playing")
  speaker.audioStopAt(2)
  waitAt(2, "idle", 5)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "endpoint 1 did not survive audioStopAt(2)")
  waitTimer(6.0, "only speaker #1 should continue")
end, false)

gate("L6/8 Continuous RAW", {
  "This confirms the RAW continuation fix.",
  "Both speakers should produce one continuous",
  "440 Hz tone for about 6 seconds.",
  "Fail for early cutoff, gaps, crackle, or desync.",
}, function()
  local chunk = makeRawChunk(96000, 440, 15000)
  for i = 1, 3 do
    sendRawAll(chunk, 0.45, 8)
    log("RAW", "accepted 2-second chunk " .. i .. "/3")
  end
  waitTimer(4.0, "allowing final RAW buffer to finish")
end, false)

gate("L7/8 Range leave/rejoin", {
  "A looping group MP3 will start.",
  "Leave speaker audible range, then come back.",
  "It should recover to the current playback cleanly.",
  "Return to this computer and judge the result.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.55), "group MP3 rejected")
  verifySharedPlaying()
  assert(speaker.audioSetLooping(true), "loop enable failed")
  state.detail = "leave range, return, then press ENTER"
  state.prompt = {"Audio stays running.", "Walk away and come back.", "Press ENTER after checking recovery."}
  render()
  print("Playback is looping. Leave audible range and come back, then press ENTER.")
  waitEnter()
  local s = speaker.audioStatusAt(1)
  assert(s.state == "playing" or s.state == "paused", "server playback no longer active after range test")
end, false)

gate("L8/8 Resource reload recovery", {
  "A looping group MP3 will start.",
  "Exit the computer GUI and press F3+T.",
  "Wait for resource reload, then return here.",
  "Audio should recover without restarting server state.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.55), "group MP3 rejected")
  verifySharedPlaying()
  assert(speaker.audioSetLooping(true), "loop enable failed")
  state.detail = "perform F3+T, return, then press ENTER"
  state.prompt = {"Exit GUI.", "Press F3+T.", "Return after reload.", "Press ENTER here."}
  render()
  print("Exit the GUI, press F3+T, wait for reload, return here, then press ENTER.")
  waitEnter()
  local s = speaker.audioStatusAt(1)
  assert(s.state == "playing" or s.state == "paused", "server playback no longer active after reload test")
end, false)

-- ============================================================================
-- OPTIONAL ENVIRONMENT-SPECIFIC CHECKS
-- ============================================================================

gate("O1 Moving-source tracking", {
  "OPTIONAL: use this only with speakers on",
  "Sable/Aeronautics or Valkyrien Skies movement.",
  "After ENTER, move the speaker platform for 15 sec.",
  "The sound must move with the physical speakers.",
}, function()
  local before = speaker.getSpeakerPos(1)
  assert(speaker.speakMp3All(mp3, 0.55), "moving-source group start rejected")
  verifySharedPlaying()

  local moved = false
  local deadline = os.startTimer(15)
  local poll = os.startTimer(0.25)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    noteEvent(e)
    if e[1] == "timer" and e[2] == deadline then break end
    if e[1] == "timer" and e[2] == poll then
      local now = speaker.getSpeakerPos(1)
      if not samePos(before, now) then
        moved = true
        log("POS", "speaker 1 moved from " .. serialize(before) .. " to " .. serialize(now))
      end
      poll = os.startTimer(0.25)
    end
  end
  assert(moved, "no projected speaker movement was observed")
end, true)

gate("O2 Sound Physics integration", {
  "OPTIONAL: run only with Sound Physics Remastered.",
  "Start playback, then test wall/room occlusion/reverb.",
  "HQ audio should respond like a physical sound source.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.55), "SPR group start rejected")
  verifySharedPlaying()
  state.detail = "test occlusion/reverb, then press ENTER"
  state.prompt = {"Move around walls/rooms.", "Check occlusion/reverb.", "Press ENTER when done."}
  render()
  print("Test Sound Physics occlusion/reverb, then return and press ENTER.")
  waitEnter()
end, true)

if RADIO_URL then
  gate("O3 Group MP3/ICY radio", {
    "OPTIONAL radio URL supplied.",
    "All attached speakers should start together",
    "after prebuffer and remain synchronized for 20 sec.",
    "Fail for drift, staggered start, or dropouts.",
  }, function()
    assert(speaker.speakStreamAll(RADIO_URL, 0.45), "group radio rejected URL")
    for i = 1, speaker.getSpeakerCount() do waitAt(i, "playing", 20, "radio endpoint " .. i) end
    assert(speaker.isStreaming(), "radio ownership did not remain active")
    log("META", serialize(speaker.getStreamMeta()))
    waitTimer(20.0, "group radio long-run sync")
  end, true)

  gate("O4 Radio strict membership", {
    "OPTIONAL: requires one extra speaker you can add now.",
    "A radio group starts with the CURRENT speakers.",
    "During playback attach/add another speaker.",
    "The new speaker must stay silent until group rerun.",
  }, function()
    local initial = speaker.getSpeakerCount()
    assert(speaker.speakStreamAll(RADIO_URL, 0.40), "strict-membership radio start rejected")
    for i = 1, initial do waitAt(i, "playing", 20, "radio endpoint " .. i) end

    state.detail = "add another speaker, then press ENTER"
    state.prompt = {"Add/connect one new speaker now.", "It must stay silent.", "Press ENTER after checking."}
    render()
    print("Add/connect one new speaker while radio is playing. It must remain silent. Press ENTER after checking.")
    waitEnter()

    local after = speaker.getSpeakerCount()
    assert(after > initial, "speaker count did not increase; add a new speaker or skip this check")

    speaker.audioStopAll()
    assert(speaker.speakStreamAll(RADIO_URL, 0.40), "radio rerun rejected")
    for i = 1, after do waitAt(i, "playing", 20, "rerun radio endpoint " .. i) end

    state.detail = "new speaker should now join; press ENTER"
    state.prompt = {"After rerun, all speakers should play.", "Press ENTER after checking."}
    render()
    print("After rerun, the new speaker should now join. Press ENTER after checking.")
    waitEnter()
  end, true)
else
  state.optionalSkipped = state.optionalSkipped + 2
  log("SKIP", "O3/O4 radio checks: no radio URL supplied")
end

safeStop()
dumpSnapshot("master acceptance complete")

state.mode = "DONE"
state.test = "Master acceptance complete"
state.result = state.optionalSkipped > 0 and "PASS+SKIPS" or "FULL PASS"
state.detail = "required core passed"
state.prompt = {
  "REQUIRED CORE PASSED",
  ("Optional passed %d, skipped %d"):format(state.optionalPassed, state.optionalSkipped),
  "Send /v10-acceptance.log if needed.",
}
render()

log("PASS", "ALL REQUIRED MASTER ACCEPTANCE CHECKS PASSED")
log("INFO", ("optional passed=%d skipped=%d"):format(state.optionalPassed, state.optionalSkipped))
print("")
print(state.optionalSkipped > 0 and "[PASS] v10 MASTER core acceptance (optional checks skipped)"
  or "[PASS] v10 MASTER FULL acceptance")
print("Log: " .. LOG)
