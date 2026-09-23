-- CC:HQ Speakers v10 diagnostic master runtime acceptance.
-- Usage:
--   v10_acceptance <mp3> <wav> [radio-url]
--
-- One file, one command, one monitor dashboard, one log.
-- The mod's built-in diagnostics judge the real client/OpenAL playback automatically.
-- You only perform physical actions Minecraft cannot perform itself (walk away, F3+T, move Sable, etc.).
-- Target scope for this runner: singleplayer + Sable/Aeronautics. VS2 and dedicated-server testing are intentionally out of scope.
--
-- Keys:
--   ENTER = confirm a requested physical action / start an environment check
--   S     = skip an environment check which is not available

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
  autoTotal = 19,
  runtimePassed = 0,
  runtimeTotal = 9,
  optionalPassed = 0,
  optionalSkipped = 0,
  optionalTotal = 6,
  skippedNames = {},
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
    writeAt(monitor, 3, ("Auto %d/%d | Runtime %d/%d | Speakers %d")
      :format(state.autoPassed, state.autoTotal, state.runtimePassed, state.runtimeTotal, speaker.getSpeakerCount()))
    writeAt(monitor, 4, ("Target %d/%d pass | %d skipped")
      :format(state.optionalPassed, state.optionalTotal, state.optionalSkipped))
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

local function writeBinary(path, data)
  local h = assert(fs.open(path, "wb"), "cannot create " .. path)
  h.write(data)
  h.close()
end

local function expectError(label, fn)
  local ok, err = pcall(fn)
  assert(not ok, label .. " was unexpectedly accepted")
  log("REJECT", label .. " -> " .. shorten(err, 120))
end

local function markSkipped(name)
  state.optionalSkipped = state.optionalSkipped + 1
  state.skippedNames[#state.skippedNames + 1] = name
  log("MISSING", name)
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
  pcall(function() if speaker.hqDiagEnable then speaker.hqDiagEnable(false) end end)
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

local function actionGate(name, instructions, action, optional)
  safeStop()
  display(optional and "OPTION" or "ACTION", name, "press ENTER when ready", instructions)
  print("")
  print("=== " .. name .. " ===")
  for _, line in ipairs(instructions) do print(line) end
  if optional then print("Press ENTER to run, or S to skip.") else print("Press ENTER when ready.") end

  if optional then
    while true do
      local e = {os.pullEventRaw()}
      if e[1] == "terminate" then error("terminated", 0) end
      if e[1] == "char" and string.lower(e[2]) == "s" then
        markSkipped(name)
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

  state.detail = "perform the requested action; diagnostics decide PASS/FAIL"
  render()
  log("ACTION", name .. " started")

  local ok, err = pcall(action)
  safeStop()
  if not ok then fail(name, err) end

  if optional then state.optionalPassed = state.optionalPassed + 1
  else state.runtimePassed = state.runtimePassed + 1 end
  log("PASS", name .. " = automatic diagnostic PASS")
  state.prompt = {}
  render()
  return "pass"
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
    local s = waitAt(i, "playing", 15, "group endpoint " .. i)    if id and s.playbackId then
      assert(s.playbackId == id, "playbackId mismatch at endpoint " .. i)
    end
  end
  return first
end


local DIAG_START_SKEW_MS = 75
local DIAG_LOGICAL_DRIFT_MS = 50
local DIAG_PCM_SPREAD_BYTES = 65536
local DIAG_POSITION_MOVE = 1.0

local function diagReset(label)
  safeStop()
  waitTimer(0.25, "clearing previous client audio")
  local epoch = speaker.hqDiagReset()
  log("DIAG", ("reset %s epoch=%s"):format(label, tostring(epoch)))
  return epoch
end

local function diagSnapshot(label, settle)
  if settle and settle > 0 then waitTimer(settle, "collecting client diagnostics") end
  local snap = speaker.hqDiagSnapshot()
  log("DIAG", label .. " = " .. serialize(snap))
  return snap
end

local function diagSources(snapshot, kind)
  local out = {}
  for _, source in ipairs(snapshot.sources or {}) do
    if not kind or source.kind == kind then out[#out + 1] = source end
  end
  return out
end

local function diagLargestGroup(snapshot, prefix)
  local best = nil
  for _, group in ipairs(snapshot.groups or {}) do
    if not prefix or tostring(group.group or ""):sub(1, #prefix) == prefix then
      if not best or (group.sourceCount or 0) > (best.sourceCount or 0) then best = group end
    end
  end
  return best
end

local function sourceForEndpoint(snapshot, index, kind)
  local p = speaker.getSpeakerPos(index)
  for _, source in ipairs(snapshot.sources or {}) do
    if (not kind or source.kind == kind)
      and math.abs((source.blockX or 0) - p.x) < 0.01
      and math.abs((source.blockY or 0) - p.y) < 0.01
      and math.abs((source.blockZ or 0) - p.z) < 0.01 then
      return source
    end
  end
  return nil
end

local function sourceById(snapshot, id)
  for _, source in ipairs(snapshot.sources or {}) do
    if source.source == id then return source end
  end
end

local function assertClientBridge(snapshot)
  local caps = snapshot.capabilities or {}
  assert(caps.clientSeen == true, "client diagnostics never saw an HQ audio channel")
  assert(caps.integratedServer == true,
    "diagnostic acceptance expects singleplayer/integrated server; dedicated-server mode is out of scope")
  assert(type(caps.openAlRenderer) == "string", "OpenAL renderer diagnostics missing")
end

local function assertSourceHealthy(source, label, minimumPlayingSamples, requireContinuous)
  assert(source, label .. ": diagnostic source missing")
  assert((source.channelStarts or 0) >= 1, label .. ": Minecraft/OpenAL channel never started")
  assert((source.playingSamples or 0) >= (minimumPlayingSamples or 3),
    label .. ": OpenAL source was not observed playing long enough")
  assert((source.decoderFailures or 0) == 0, label .. ": decoder failure observed")
  if requireContinuous then
    assert((source.playingToStoppedTransitions or 0) == 0,
      label .. ": OpenAL source stopped unexpectedly while playback should have remained active")
  end
end

local function assertFiniteGroup(snapshot, expected, label, checkDrift, requireContinuous)
  assertClientBridge(snapshot)
  local sources = diagSources(snapshot, "finite")
  assert(#sources == expected, ("%s: expected %d finite client sources, got %d"):format(label, expected, #sources))
  for i, source in ipairs(sources) do
    assertSourceHealthy(source, label .. " source " .. i, 5, requireContinuous ~= false)
  end

  local group = diagLargestGroup(snapshot, "finite:")
  assert(group, label .. ": finite sync group missing")
  assert((group.sourceCount or 0) == expected,
    ("%s: diagnostic group has %s/%d sources"):format(label, tostring(group.sourceCount), expected))
  assert((group.playingMembers or 0) == expected,
    ("%s: only %s/%d sources ever reached PLAYING"):format(label, tostring(group.playingMembers), expected))
  assert(type(group.startSkewMs) == "number" and group.startSkewMs >= 0,
    label .. ": PLAYING start-skew measurement missing")
  assert(group.startSkewMs <= DIAG_START_SKEW_MS,
    ("%s: observed PLAYING start skew %.2f ms exceeds %.0f ms"):format(label, group.startSkewMs, DIAG_START_SKEW_MS))
  local channelSkew = group.channelStartSkewMs
  assert(type(channelSkew) == "number" and channelSkew >= 0,
    label .. ": real channel-start measurement missing")
  assert(channelSkew <= DIAG_START_SKEW_MS,
    ("%s: real channel start skew %.2f ms exceeds %.0f ms"):format(label, channelSkew, DIAG_START_SKEW_MS))
  if checkDrift ~= false then
    local logical = group.maxLogicalAudibleOffsetSpreadMs
    assert(type(logical) == "number",
      label .. ": canonical client drift measurement missing")
    assert(logical <= DIAG_LOGICAL_DRIFT_MS,
      ("%s: canonical playback drift %.2f ms exceeds %.0f ms"):format(
        label, logical, DIAG_LOGICAL_DRIFT_MS))
  end
  assert((group.pcmReadBytesSpread or 0) <= DIAG_PCM_SPREAD_BYTES,
    ("%s: decoder/render feed spread %d bytes exceeds %d"):format(
      label, group.pcmReadBytesSpread or -1, DIAG_PCM_SPREAD_BYTES))
  log("MEASURE", ("%s channelStart=%.2fms observedStart=%.2fms logicalDrift=%.2fms rawOffsetSpread=%.2fms pcmSpread=%dB"):format(
    label, group.channelStartSkewMs or -1, group.startSkewMs or -1,
    group.maxLogicalAudibleOffsetSpreadMs or -1,
    group.maxAudibleOffsetSpreadMs or -1, group.pcmReadBytesSpread or -1))
  return sources, group
end

local function assertRawGroup(snapshot, expected, expectedBytes, label)
  assertClientBridge(snapshot)
  local sources = diagSources(snapshot, "raw")
  assert(#sources == expected, ("%s: expected %d RAW client sources, got %d"):format(label, expected, #sources))
  for i, source in ipairs(sources) do
    assertSourceHealthy(source, label .. " source " .. i, 3)
    assert((source.pcmInputBytes or 0) == expectedBytes,
      ("%s source %d: expected %d RAW bytes in, got %d"):format(label, i, expectedBytes, source.pcmInputBytes or -1))
    assert((source.pcmReadBytes or 0) >= expectedBytes - 8192,
      ("%s source %d: client only delivered %d/%d RAW bytes to OpenAL"):format(
        label, i, source.pcmReadBytes or -1, expectedBytes))
  end
  local group = diagLargestGroup(snapshot, "raw:")
  assert(group and (group.sourceCount or 0) == expected, label .. ": RAW sync group missing members")
  assert((group.playingMembers or 0) == expected, label .. ": a RAW endpoint never reached PLAYING")
  assert(type(group.channelStartSkewMs) == "number" and group.channelStartSkewMs >= 0,
    label .. ": RAW channel-start measurement missing")
  assert(group.channelStartSkewMs <= DIAG_START_SKEW_MS,
    ("%s: RAW channel start skew %.2f ms exceeds %.0f ms"):format(
      label, group.channelStartSkewMs, DIAG_START_SKEW_MS))
  assert((group.maxAudibleOffsetSpreadMs or 999999) <= DIAG_LOGICAL_DRIFT_MS,
    ("%s: RAW playback drift %.2f ms exceeds %.0f ms"):format(
      label, group.maxAudibleOffsetSpreadMs or -1, DIAG_LOGICAL_DRIFT_MS))
  log("MEASURE", ("%s channelStart=%.2fms drift=%.2fms pcmSpread=%dB"):format(
    label, group.channelStartSkewMs or -1, group.maxAudibleOffsetSpreadMs or -1,
    group.pcmReadBytesSpread or -1))
  return sources, group
end

local function runtimeDiag(name, fn)
  display("RUNTIME", name, "automatic client/audio diagnostic", {})
  log("BEGIN", name)
  local ok, err = pcall(fn)
  safeStop()
  if not ok then fail(name, err) end
  state.runtimePassed = state.runtimePassed + 1
  log("PASS", name .. " = automatic diagnostic PASS")
  render()
end

local function waitUntil(predicate, timeout, detail)
  local started = os.clock()
  while os.clock() - started < timeout do
    if predicate() then return true end
    waitTimer(0.10, detail)
  end
  return predicate()
end


-- Fresh diagnostics.
do
  local h = fs.open(LOG, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 diagnostic master acceptance")
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

auto("A1/19 Frozen API surface", function()
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
    "hqDiagEnable", "hqDiagReset", "hqDiagSnapshot", "hqDiagCapabilities",
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

auto("A2/19 Discovery + declared limits", function()
  assert(speaker.getPeripheralType() == "speaker", "peripheral type changed")
  assert(speaker.getSpeakerCount() == speakerCount, "speaker count changed during startup")
  assert(speaker.speakSampleRate() == 48000, "RAW sample rate changed")
  assert(speaker.speakMaxSamples() == 131072, "RAW max samples changed")
  assert(speaker.speakMaxAudioBytes() == 8 * 1024 * 1024, "finite byte limit changed")
  assert(type(speaker.audioMaxStagedBytes()) == "number" and speaker.audioMaxStagedBytes() >= #mp3,
    "staging capacity is smaller than the test MP3")

  local prepared = speaker.audioPreparedFormats()
  assert(prepared.mp3 and prepared.wav, "prepared MP3/WAV formats missing")

  local files = {}
  for _, ext in pairs(speaker.speakSupportedFiles()) do files[ext] = true end
  assert(files.mp3 and files.wav and not files.ogg, "finite format set changed")

  local streams = speaker.getStreamFormats()
  assert(type(streams) == "table" and streams.mp3 and streams.hls == nil and streams.ts == nil,
    "stream format set changed")

  local discovered = speaker.getSpeakers()
  assert(type(discovered) == "table" and #discovered == speakerCount, "speaker discovery count mismatch")
  for i = 1, speakerCount do
    local p = speaker.getSpeakerPos(i)
    assert(type(p) == "table" and type(p.x) == "number"
      and type(p.y) == "number" and type(p.z) == "number", "bad position at endpoint " .. i)
  end
end)

auto("A3/19 Staged media lifecycle", function()
  local mount = speaker.audioMountPath()
  assert(type(mount) == "string" and #mount > 0, "staging mount missing")
  local relative = "v10-acceptance-staged-" .. os.getComputerID() .. ".mp3"
  local full = fs.combine(mount, relative)
  local assetId = nil

  local function cleanup()
    pcall(function() speaker.audioStopAll() end)
    pcall(function() speaker.audioStop() end)
    if assetId then pcall(function() speaker.audioReleasePrepared(assetId) end) end
    if fs.exists(full) then pcall(fs.delete, full) end
  end

  local ok, err = pcall(function()
    if fs.exists(full) then fs.delete(full) end
    writeBinary(full, mp3)
    assetId = speaker.audioPrepareStaged(relative, false)
    assert(type(assetId) == "string" and #assetId > 0, "prepare did not return an asset id")

    local info = speaker.audioPreparedInfo(assetId)
    assert(info.format == "mp3", "prepared format mismatch")
    assert(type(info.duration) == "number" and info.duration > 1, "prepared duration missing")
    assert(info.sizeBytes == #mp3, "prepared size mismatch")

    assert(speaker.audioPlayPrepared(assetId, 0.0), "single prepared playback rejected")
    waitStatus(function() return speaker.audioStatus() end, "playing", 15, "prepared single")
    speaker.audioStop()
    waitStatus(function() return speaker.audioStatus() end, "idle", 5, "prepared single")

    assert(speaker.audioPlayPreparedAll(assetId, 0.0), "prepared group playback rejected")
    verifySharedPlaying()
    speaker.audioStopAll()
    for i = 1, speakerCount do waitAt(i, "idle", 5, "prepared endpoint " .. i) end

    assert(speaker.audioReleasePrepared(assetId), "prepared asset release failed")
    assert(not speaker.audioReleasePrepared(assetId), "prepared asset released twice")
    assetId = nil
  end)

  cleanup()
  if not ok then error(err, 0) end
end)

auto("A4/19 Native CC:T methods + backpressure", function()
  assert(speaker.playNote("harp", 0.0, 12), "native playNote rejected")
  waitTimer(0.10, "native note dispatch")
  assert(speaker.playSound("minecraft:entity.experience_orb.pickup", 0.0, 1.0), "native playSound rejected")
  waitTimer(0.15, "native sound dispatch")
  speaker.stop()
  waitTimer(0.10, "native stop dispatch")

  local audio = {}
  for i = 1, 4800 do audio[i] = math.floor(math.sin(i * 0.08) * 100) end
  assert(speaker.playAudio(audio, 0.0), "first silent native buffer rejected")
  assert(speaker.playAudio(audio, 0.0) == false, "native second buffer should backpressure")
  waitEvent("speaker_audio_empty", 5, "waiting for native capacity")
  assert(speaker.playAudio(audio, 0.0), "native retry rejected")
  speaker.stop()
end)

auto("A5/19 MP3 lifecycle + EOF", function()
  assert(speaker.speakMp3(mp3, 0.0), "MP3 rejected")
  local s = waitStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3")
  assert(type(s.duration) == "number" and s.duration > 1, "MP3 duration missing")
  assert(speaker.audioPause(), "pause failed")
  waitStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3")
  assert(speaker.audioResume(), "resume failed")
  waitStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3")
  assert(speaker.audioSeek(math.min(1.0, s.duration * 0.25)), "seek failed")
  assert(speaker.audioSeek(s.duration), "seek-to-end failed")
  waitStatus(function() return speaker.audioStatus() end, "ended", 5, "MP3")
  assert(not speaker.speakIsPlaying(), "speakIsPlaying remained true after finite EOF")
  speaker.audioStop()
end)

auto("A6/19 Loop-wrap authority", function()
  assert(speaker.speakMp3(mp3, 0.0), "MP3 rejected")
  local s = waitStatus(function() return speaker.audioStatus() end, "playing", 15, "loop MP3")
  assert(speaker.audioSetLooping(true), "loop enable failed")
  assert(speaker.audioSeek(math.max(0, s.duration - 0.35)), "near-end seek failed")
  waitTimer(1.2, "crossing loop boundary")
  local after = speaker.audioStatus()
  assert(after.state == "playing", "loop did not remain playing")
  assert(after.looping == true, "loop flag was lost")
  assert(type(after.position) == "number" and after.position < 5.0,
    "position did not wrap near the start")
  assert(speaker.audioSetLooping(false), "loop disable failed")
  speaker.audioStop()
end)

auto("A7/19 WAV lifecycle", function()
  assert(speaker.speakWav(wav, 0.0), "WAV rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV")
  speaker.audioStop()
  waitStatus(function() return speaker.audioStatus() end, "idle", 5, "WAV")
end)

auto("A8/19 Malformed finite-media rejection", function()
  expectError("empty MP3", function() speaker.speakMp3("", 0.0) end)
  expectError("garbage MP3", function() speaker.speakMp3("this is not an mp3", 0.0) end)
  expectError("truncated MP3", function() speaker.speakMp3(mp3:sub(1, math.min(24, #mp3)), 0.0) end)
  expectError("WAV passed to speakMp3", function() speaker.speakMp3(wav, 0.0) end)

  expectError("empty WAV", function() speaker.speakWav("", 0.0) end)
  expectError("garbage WAV", function() speaker.speakWav("RIFFbad data", 0.0) end)
  expectError("truncated WAV", function() speaker.speakWav(wav:sub(1, math.min(24, #wav)), 0.0) end)
  expectError("MP3 passed to speakWav", function() speaker.speakWav(mp3, 0.0) end)
end)

auto("A9/19 Argument and RAW bounds", function()
  expectError("empty RAW", function() speaker.speakPCM({}, 0.0) end)
  expectError("RAW +32768", function() speaker.speakPCM({32768}, 0.0) end)
  expectError("RAW -32769", function() speaker.speakPCM({-32769}, 0.0) end)
  expectError("RAW non-number", function() speaker.speakPCM({"bad"}, 0.0) end)
  expectError("RAW infinite sample", function() speaker.speakPCM({math.huge}, 0.0) end)
  expectError("non-finite default volume", function() speaker.speakVolume(math.huge) end)
  expectError("speaker index above snapshot", function() speaker.audioStatusAt(speaker.getSpeakerCount() + 1) end)

  local oversized = {}
  for i = 1, speaker.speakMaxSamples() + 1 do oversized[i] = 0 end
  expectError("RAW maxSamples+1", function() speaker.speakPCM(oversized, 0.0) end)
  oversized = nil
  collectgarbage()

  assert(speaker.speakMp3(mp3, 0.0), "MP3 rejected for finite-argument checks")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "finite argument check")
  expectError("non-finite seek", function() speaker.audioSeek(math.huge) end)
  expectError("non-finite finite volume", function() speaker.audioSetVolume(math.huge) end)
  speaker.audioStop()
end)

auto("A10/19 RAW backpressure + retry", function()
  local chunk = makeRawChunk(24000, 420, 22000)  local accepted, rejected = 0, false
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

auto("A11/19 RAW All/At admission", function()
  local short = makeRawChunk(4800, 500, 18000)
  assert(speaker.speakPCMAll(short, 0.0), "speakPCMAll rejected")
  waitTimer(0.15)
  speaker.audioStopAll()
  for i = 1, speakerCount do waitAt(i, "idle", 3, "RAW endpoint " .. i) end

  assert(speaker.speakPCMAt(2, short, 0.0), "speakPCMAt(2) rejected")
  waitTimer(0.10)
  speaker.audioStopAt(2)
  waitAt(2, "idle", 3, "RAW endpoint 2")
  speaker.audioStopAll()
end)

auto("A12/19 Group finite shared authority", function()
  assert(speaker.speakMp3All(mp3, 0.0), "speakMp3All rejected")
  verifySharedPlaying()
  speaker.audioStopAll()
  for i = 1, speakerCount do waitAt(i, "idle", 5) end
end)

auto("A13/19 Endpoint-local stop", function()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected")
  verifySharedPlaying()
  speaker.audioStopAt(2)
  waitAt(2, "idle", 5)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) stopped endpoint 1")
  speaker.audioStopAll()
end)

auto("A14/19 Endpoint gain + mute + clamp", function()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected")
  verifySharedPlaying()
  assert(speaker.audioSetVolumeAt(1, 99.0), "endpoint high volume set failed")
  assert(math.abs((speaker.audioStatusAt(1).volume or -1) - 3.0) < 0.001, "endpoint volume did not clamp to 3")
  assert(speaker.audioSetVolumeAt(1, -5.0), "endpoint low volume set failed")
  assert(math.abs((speaker.audioStatusAt(1).volume or -1) - 0.0) < 0.001, "endpoint volume did not clamp to 0")
  assert(speaker.audioSetMutedAt(2, true), "endpoint mute failed")
  assert(speaker.audioStatusAt(2).muted == true, "endpoint mute state missing")
  assert(speaker.audioSetMutedAt(2, false), "endpoint unmute failed")
  assert(speaker.audioSetVolumeAll(0.30), "all volume failed")
  speaker.audioStopAll()
end)

auto("A15/19 Shared pause/resume/seek", function()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected")
  local first = verifySharedPlaying()
  assert(speaker.audioPauseAll(), "group pause rejected")
  for i = 1, speakerCount do waitAt(i, "paused", 5, "paused endpoint " .. i) end
  assert(speaker.audioResumeAll(), "group resume rejected")
  for i = 1, speakerCount do waitAt(i, "playing", 5, "resumed endpoint " .. i) end
  local target = math.min(2.0, math.max(0.25, (first.duration or 10) * 0.2))
  assert(speaker.audioSeekAll(target), "group seek rejected")
  waitTimer(0.15)
  local reference = speaker.audioStatusAt(1)
  for i = 2, speakerCount do
    local s = speaker.audioStatusAt(i)
    assert(s.playbackId == reference.playbackId, "group seek changed playback authority at endpoint " .. i)
    assert(math.abs((s.position or 0) - (reference.position or 0)) < 0.20,
      "group seek positions diverged at endpoint " .. i)
  end
  speaker.audioStopAll()
end)

auto("A16/19 Concurrent control stress", function()
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

auto("A17/19 Repeated synchronized starts", function()
  for cycle = 1, 4 do
    assert(speaker.speakMp3All(mp3, 0.0), "repeat start rejected " .. cycle)
    verifySharedPlaying()
    speaker.audioStopAll()
    for i = 1, speakerCount do waitAt(i, "idle", 5) end
  end
end)

auto("A18/19 Stream security + metadata surface", function()
  assert(not speaker.isStreaming(), "stream unexpectedly active before radio test")
  expectError("file:// stream URL", function() speaker.speakStream("file:///tmp/nope.mp3", 0.0) end)
  expectError("loopback stream URL", function() speaker.speakStream("http://127.0.0.1:8000/nope.mp3", 0.0) end)
  local meta = speaker.getStreamMeta()
  assert(type(meta) == "table", "stream metadata not a table")
  assert(type(speaker.getStreamTitle()) == "string", "stream title getter changed")
  assert(type(speaker.getStreamArtist()) == "string", "stream artist getter changed")
  assert(type(speaker.getStreamSong()) == "string", "stream song getter changed")
  assert(type(speaker.getStreamStation()) == "string", "stream station getter changed")
  assert(type(speaker.getStreamGenre()) == "string", "stream genre getter changed")
  assert(type(speaker.getStreamMetaSerial()) == "number", "stream metadata serial changed")
end)

auto("A19/19 Final deterministic idle", function()
  safeStop()
  for i = 1, speakerCount do waitAt(i, "idle", 5) end
  assert(not speaker.isStreaming(), "stream ownership remained active after cleanup")
end)

log("PASS", "ALL AUTOMATED CORE CHECKS PASSED")

-- Built-in diagnostics are dormant during normal play. Enable them only for this acceptance run.
local diagnosticEpoch = speaker.hqDiagEnable(true)
log("DIAG", "built-in diagnostics enabled epoch=" .. tostring(diagnosticEpoch))

-- ============================================================================
-- REQUIRED REAL-CLIENT / OPENAL DIAGNOSTICS
-- These are judged by the mod. No human PASS/FAIL input.
-- ============================================================================

runtimeDiag("R1/9 Native CC:T playAudio client channel", function()
  diagReset("native CC:T")
  local audio = {}
  for i = 1, 48000 do audio[i] = math.floor(math.sin(i * 0.08) * 90) end
  assert(speaker.playAudio(audio, 0.8), "native playAudio rejected")
  waitTimer(0.70, "observing native CC:T client channel")
  local snap = diagSnapshot("native CC:T")
  assertClientBridge(snap)
  local sources = diagSources(snap, "native")
  assert(#sources >= 1, "CC:T native DFPWM stream never reached a real client audio channel")
  assertSourceHealthy(sources[1], "native CC:T", 3)
  waitTimer(0.5, "letting native buffer finish")
end)

runtimeDiag("R2/9 MP3 pause/resume renderer", function()
  diagReset("MP3 pause/resume")
  assert(speaker.speakMp3(mp3, 0.65), "MP3 rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "MP3")
  waitTimer(3.0, "MP3 playing")
  assert(speaker.audioPause(), "pause failed")
  waitStatus(function() return speaker.audioStatus() end, "paused", 5, "MP3")
  waitTimer(2.0, "MP3 paused")
  assert(speaker.audioResume(), "resume failed")
  waitStatus(function() return speaker.audioStatus() end, "playing", 5, "MP3")
  waitTimer(3.0, "MP3 resumed")
  local snap = diagSnapshot("MP3 pause/resume")
  assertClientBridge(snap)
  local sources = diagSources(snap, "finite")
  assert(#sources == 1, "expected exactly one MP3 client source")
  local source = sources[1]
  assertSourceHealthy(source, "MP3", 20, true)
  assert((source.pausedSamples or 0) >= 5, "OpenAL source was never observed paused")
  assert((source.channelStarts or 0) == 1, "pause/resume unexpectedly recreated the client audio channel")
  assert((source.recoveryRejoins or 0) == 0, "pause/resume triggered recovery unexpectedly")
  log("MEASURE", ("MP3 playingSamples=%d pausedSamples=%d pcmRead=%d silence=%d"):format(
    source.playingSamples or 0, source.pausedSamples or 0,
    source.pcmReadBytes or 0, source.silenceReadBytes or 0))
end)

runtimeDiag("R3/9 WAV renderer continuity", function()
  diagReset("WAV continuity")
  assert(speaker.speakWav(wav, 0.65), "WAV rejected")
  waitStatus(function() return speaker.audioStatus() end, "playing", 15, "WAV")
  waitTimer(8.0, "WAV client playback")
  local snap = diagSnapshot("WAV continuity")
  assertClientBridge(snap)
  local sources = diagSources(snap, "finite")
  assert(#sources == 1, "expected exactly one WAV client source")
  local source = sources[1]
  assertSourceHealthy(source, "WAV", 30, true)
  assert((source.channelStarts or 0) == 1, "WAV renderer restarted unexpectedly")
  assert((source.recoveryRejoins or 0) == 0, "WAV triggered recovery unexpectedly")
  assert((source.pcmReadBytes or 0) > 100000, "WAV decoder did not feed meaningful PCM to OpenAL")
end)

runtimeDiag("R4/9 Multispeaker finite synchronization", function()
  diagReset("finite group sync")
  local expected = speaker.getSpeakerCount()
  assert(speaker.speakMp3All(mp3, 0.60), "group MP3 rejected")
  verifySharedPlaying()
  waitTimer(12.0, "measuring all-speaker synchronization")
  local snap = diagSnapshot("finite group sync")
  assertFiniteGroup(snap, expected, "finite group sync", true, true)
end)

runtimeDiag("R5/9 Endpoint-local stop reaches client", function()
  diagReset("endpoint-local stop")
  local expected = speaker.getSpeakerCount()
  assert(speaker.speakMp3All(mp3, 0.60), "group MP3 rejected")
  verifySharedPlaying()
  waitTimer(4.0, "establishing group playback")
  local before = diagSnapshot("endpoint stop before")
  assertFiniteGroup(before, expected, "endpoint stop before", true, true)

  local targetBefore = assert(sourceForEndpoint(before, 2, "finite"), "endpoint 2 diagnostic source missing")
  local survivorBefore = assert(sourceForEndpoint(before, 1, "finite"), "endpoint 1 diagnostic source missing")
  speaker.audioStopAt(2)
  waitAt(2, "idle", 5, "endpoint 2")
  waitTimer(3.0, "checking endpoint-local client stop")
  local after = diagSnapshot("endpoint stop after")
  local target = assert(sourceById(after, targetBefore.source), "endpoint 2 diagnostic history disappeared")
  local survivor = assert(sourceById(after, survivorBefore.source), "endpoint 1 diagnostic history disappeared")

  assert((target.channelDetaches or 0) >= 1, "audioStopAt(2) never detached endpoint 2's real client channel")
  assert((survivor.channelDetaches or 0) == 0, "audioStopAt(2) detached endpoint 1's client channel")
  assert((survivor.playingSamples or 0) > (survivorBefore.playingSamples or 0),
    "endpoint 1 did not continue rendering after endpoint 2 stopped")
  local serverSurvivor = speaker.audioStatusAt(1)
  assert(serverSurvivor.state == "playing", "endpoint 1 server playback did not survive audioStopAt(2)")
end)

runtimeDiag("R6/9 Continuous RAW client delivery", function()
  diagReset("continuous RAW")
  local expectedSources = speaker.getSpeakerCount()
  local samplesPerChunk = 96000
  local chunks = 3
  local expectedBytes = samplesPerChunk * 2 * chunks
  local chunk = makeRawChunk(samplesPerChunk, 440, 15000)
  for i = 1, chunks do
    sendRawAll(chunk, 0.45, 10)
    log("RAW", "accepted 2-second chunk " .. i .. "/" .. chunks)
  end
  assert(waitUntil(function() return not speaker.speakIsPlaying() end, 10, "waiting for RAW drain"),
    "RAW server lifetime did not drain")
  local snap = diagSnapshot("continuous RAW", 0.5)
  local sources, group = assertRawGroup(snap, expectedSources, expectedBytes, "continuous RAW")
  for i, source in ipairs(sources) do
    assert((source.channelStarts or 0) == 1,
      "RAW source " .. i .. " recreated its channel instead of continuing producer-fed playback")
    log("MEASURE", ("RAW source %d input=%d read=%d emptyReads=%d pumpWakes=%d"):format(
      i, source.pcmInputBytes or 0, source.pcmReadBytes or 0,
      source.emptyReads or 0, source.pumpWakes or 0))
  end
  assert((group.pcmReadBytesSpread or 0) <= 8192, "RAW endpoints consumed materially different PCM amounts")
end)

runtimeDiag("R7/9 Loop-boundary client recovery + sync", function()
  diagReset("loop boundary")
  local expected = speaker.getSpeakerCount()
  assert(speaker.speakMp3All(mp3, 0.55), "loop group rejected")
  local status = verifySharedPlaying()
  assert(speaker.audioSetLoopingAll(true), "loop enable failed")
  assert(speaker.audioSeekAll(math.max(0, (status.duration or 40) - 2.0)), "near-end seek failed")
  waitTimer(0.8, "settling after near-end seek")
  local before = diagSnapshot("loop before wrap")
  local beforeSources = diagSources(before, "finite")
  assert(#beforeSources == expected, "loop pre-wrap client source count mismatch")
  local starts = {}
  local eof = {}
  for _, source in ipairs(beforeSources) do
    starts[source.source] = source.channelStarts or 0
    eof[source.source] = source.eofCount or 0
  end

  waitTimer(3.0, "crossing loop boundary")
  local after = diagSnapshot("loop after wrap")
  assertFiniteGroup(after, expected, "loop after wrap", false, false)
  for _, source in ipairs(diagSources(after, "finite")) do
    assert((source.channelStarts or 0) > (starts[source.source] or 0),
      "loop boundary did not create a fresh renderer epoch for " .. tostring(source.source))
    assert((source.eofCount or 0) > (eof[source.source] or 0),
      "loop boundary EOF was not observed for " .. tostring(source.source))
    assert((source.decoderFailures or 0) == 0, "decoder failure during loop wrap")
  end
  local server = speaker.audioStatusAt(1)
  assert(server.state == "playing" and server.looping == true, "server loop authority was lost")
end)

local function startRecoveryPlayback(label)
  diagReset(label)
  assert(speaker.speakMp3All(mp3, 0.55), label .. ": group start rejected")
  local s = verifySharedPlaying()
  assert(speaker.audioSetLoopingAll(true), label .. ": loop enable failed")
  waitTimer(2.0, label .. ": baseline playback")
  local baseline = diagSnapshot(label .. " baseline")
  assertFiniteGroup(baseline, speaker.getSpeakerCount(), label .. " baseline", true, true)
  return s.playbackId, baseline
end

local function verifyRecoveryPlayback(label, playbackId, baseline, requireReload)
  local first = waitAt(1, "playing", 15, label .. " endpoint 1")
  assert(first.playbackId == playbackId, label .. ": playbackId changed/restarted")
  for i = 2, speaker.getSpeakerCount() do
    local s = waitAt(i, "playing", 15, label .. " endpoint " .. i)
    assert(s.playbackId == playbackId, label .. ": endpoint " .. i .. " changed playbackId")
  end
  waitTimer(2.0, label .. ": measuring recovered renderer")
  local after = diagSnapshot(label .. " recovered")
  assertFiniteGroup(after, speaker.getSpeakerCount(), label .. " recovered", false, false)
  if requireReload then assert((after.soundEngineReloads or 0) >= 1, label .. ": F3+T sound-engine reload was not observed") end

  for _, old in ipairs(diagSources(baseline, "finite")) do
    local now = assert(sourceById(after, old.source), label .. ": source history missing after recovery")
    assert((now.channelStarts or 0) > (old.channelStarts or 0),
      label .. ": client channel was not recreated after leaving/reloading")
    assert((now.decoderFailures or 0) == 0, label .. ": decoder failure during recovery")
  end
  return after
end

actionGate("R8/9 Range leave/rejoin", {
  "A looping group will start automatically.",
  "Walk more than 32 blocks away from the speakers, then return.",
  "Keep the source chunk loaded. Reopen this computer and press ENTER.",
  "Do not judge the sound yourself; diagnostics decide.",
}, function()
  local id, baseline = startRecoveryPlayback("range recovery")
  state.detail = "walk >32 blocks away, return, reopen, ENTER"
  state.prompt = {"Walk >32 blocks away.", "Return to the speakers.", "Reopen this computer.", "Press ENTER."}
  render()
  waitEnter()
  verifyRecoveryPlayback("range recovery", id, baseline, false)
end, false)

actionGate("R9/9 F3+T resource reload recovery", {
  "A looping group will start automatically.",
  "Exit the computer GUI and press F3+T.",
  "Wait for resource reload, return and reopen this computer, then ENTER.",
  "Diagnostics verify the real sound engine was rebuilt and playback rejoined.",
}, function()
  local id, baseline = startRecoveryPlayback("resource reload")
  state.detail = "perform F3+T, return, reopen, ENTER"
  state.prompt = {"Exit GUI.", "Press F3+T.", "Wait for reload.", "Return and press ENTER."}
  render()
  waitEnter()
  local after = verifyRecoveryPlayback("resource reload", id, baseline, true)
  local recovered = 0
  for _, source in ipairs(diagSources(after, "finite")) do recovered = recovered + (source.recoveryRejoins or 0) end
  assert(recovered > 0, "F3+T did not trigger an authoritative finite-renderer rejoin")
end, false)

-- ============================================================================
-- TARGET-SCOPE ENVIRONMENT / SCALE CHECKS
-- Singleplayer + Sable is the chosen release target. Dedicated server and VS2
-- are intentionally not part of this runner and do not count as missing.
-- ============================================================================

actionGate("C1 Dimension leave/rejoin", {
  "Requires the speaker/computer chunk to stay loaded.",
  "A looping group starts. Change dimension, then return to the source.",
  "Reopen this computer and press ENTER. Diagnostics judge the rejoin.",
  "Press S if you cannot keep the source chunk loaded.",
}, function()
  local id, baseline = startRecoveryPlayback("dimension recovery")
  state.detail = "keep source loaded; change dimension; return; ENTER"
  state.prompt = {"Keep source chunk loaded.", "Change dimension.", "Return to source.", "Reopen and press ENTER."}
  render()
  waitEnter()
  verifyRecoveryPlayback("dimension recovery", id, baseline, false)
end, true)

actionGate("C2 Sable/Aeronautics source tracking", {
  "The speakers/computer stay on your Sable contraption.",
  "A looping group starts. Move AND rotate the contraption for several seconds.",
  "Walk around it too, then return and press ENTER.",
  "Diagnostics measure the sound source movement; you do not judge it.",
}, function()
  diagReset("Sable tracking")
  local expected = speaker.getSpeakerCount()
  assert(speaker.speakMp3All(mp3, 0.55), "Sable group start rejected")
  verifySharedPlaying()
  assert(speaker.audioSetLoopingAll(true), "Sable loop enable failed")
  waitTimer(1.5, "Sable baseline")
  state.detail = "move and rotate Sable contraption; then ENTER"
  state.prompt = {"Move + rotate the contraption.", "Keep it moving several seconds.", "Return and press ENTER."}
  render()
  waitEnter()
  local snap = diagSnapshot("Sable tracking", 0.5)
  local sources = diagSources(snap, "finite")
  assert(#sources == expected, "Sable diagnostic source count mismatch")
  for i, source in ipairs(sources) do
    assertSourceHealthy(source, "Sable source " .. i, 5, true)
    assert((source.requestedMovement or 0) >= DIAG_POSITION_MOVE,
      ("Sable source %d: HQ sound position moved only %.3f blocks"):format(i, source.requestedMovement or 0))
    assert((source.actualMovement or 0) >= DIAG_POSITION_MOVE,
      ("Sable source %d: OpenAL source moved only %.3f blocks"):format(i, source.actualMovement or 0))
    log("MEASURE", ("Sable source %d requestedMove=%.3f actualMove=%.3f maxPosDelta=%.3f"):format(
      i, source.requestedMovement or 0, source.actualMovement or 0, source.maxPositionError or 0))
  end
end, true)
actionGate("C3 Sound Physics Remastered processing", {
  "Start with yourself in OPEN AIR near the stationary speakers.",
  "The test measures the Sound Physics filter there.",
  "Then it asks you to move behind your prepared solid wall and press ENTER.",
  "It restarts audio behind the wall and compares the real filter automatically.",
}, function()
  diagReset("SPR open air")
  local expected = speaker.getSpeakerCount()
  assert(speaker.speakMp3All(mp3, 0.55), "SPR open-air group rejected")
  verifySharedPlaying()
  waitTimer(3.0, "measuring Sound Physics open-air filter")
  local open = diagSnapshot("SPR open air")
  assertClientBridge(open)
  assert((open.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered was not detected")
  local openSources = diagSources(open, "finite")
  assert(#openSources == expected, "SPR open-air source count mismatch")
  local openByPos = {}
  for i, source in ipairs(openSources) do
    assert(source.soundPhysicsProcessed == true, "SPR did not attach a direct filter to open-air HQ source " .. i)
    assert((source.soundPhysicsSamples or 0) > 0, "SPR filter was not observable during open-air playback for source " .. i)
    openByPos[(source.blockX or 0) .. ":" .. (source.blockY or 0) .. ":" .. (source.blockZ or 0)] = source
  end

  safeStop()
  state.detail = "move behind the solid wall, then ENTER"
  state.prompt = {"Move behind prepared solid wall.", "Stay within speaker processing distance.", "Press ENTER when positioned."}
  render()
  waitEnter()

  diagReset("SPR wall")
  assert(speaker.speakMp3All(mp3, 0.55), "SPR wall group rejected")
  verifySharedPlaying()
  waitTimer(3.0, "measuring Sound Physics wall filter")
  local wall = diagSnapshot("SPR wall")
  local wallSources = diagSources(wall, "finite")
  assert(#wallSources == expected, "SPR wall source count mismatch")

  local changed = 0
  for i, source in ipairs(wallSources) do
    assert(source.soundPhysicsProcessed == true, "SPR did not process wall HQ source " .. i)
    assert((source.soundPhysicsSamples or 0) > 0, "SPR filter was not observable during wall playback for source " .. i)
    local key = (source.blockX or 0) .. ":" .. (source.blockY or 0) .. ":" .. (source.blockZ or 0)
    local before = assert(openByPos[key], "SPR could not match source position between open/wall runs")
    local gainDrop = (before.directGain or 1) - (source.directGain or 1)
    local hfDrop = (before.directGainHF or 1) - (source.directGainHF or 1)
    if gainDrop > 0.01 or hfDrop > 0.01 then changed = changed + 1 end
    log("MEASURE", ("SPR source %d gain %.4f->%.4f HF %.4f->%.4f liveRange=%.4f/%.4f"):format(
      i, before.directGain or -1, source.directGain or -1,
      before.directGainHF or -1, source.directGainHF or -1,
      source.directGainRange or 0, source.directGainHFRange or 0))
  end
  assert(changed > 0,
    "Sound Physics processed HQ audio, but the prepared wall did not measurably increase occlusion; use a more solid/thicker wall")
end, true)

if RADIO_URL then
  actionGate("C4 MP3/ICY group radio", {
    "Uses the supplied direct MP3/ICY stream URL.",
    "The test prebuffers and measures every real client source for 30 seconds.",
    "No listening verdict is required.",
  }, function()
    diagReset("group radio")
    local expected = speaker.getSpeakerCount()
    assert(speaker.speakStreamAll(RADIO_URL, 0.45), "group radio rejected URL")
    for i = 1, expected do waitAt(i, "playing", 25, "radio endpoint " .. i) end
    assert(speaker.isStreaming(), "radio ownership did not remain active")
    waitTimer(30.0, "measuring radio continuity/sync")
    local snap = diagSnapshot("group radio")
    assertClientBridge(snap)
    local sources = diagSources(snap, "stream")
    assert(#sources == expected, ("radio: expected %d client sources, got %d"):format(expected, #sources))
    for i, source in ipairs(sources) do
      assertSourceHealthy(source, "radio source " .. i, 20, true)
      assert((source.pcmReadBytes or 0) > 100000, "radio source " .. i .. " did not deliver meaningful decoded PCM")
    end
    local group = assert(diagLargestGroup(snap, "stream:"), "radio sync group missing")
    assert((group.sourceCount or 0) == expected, "radio strict group source count mismatch")
    assert((group.playingMembers or 0) == expected, "a radio endpoint never reached PLAYING")
    assert((group.channelStartSkewMs or 999999) <= 150, "radio real channel start skew exceeded 150 ms")
    assert((group.maxAudibleOffsetSpreadMs or 999999) <= 100, "radio client playback drift exceeded 100 ms")
    assert((group.pcmReadBytesSpread or 0) <= 262144, "radio decoder taps diverged by more than 256 KiB")
    log("META", serialize(speaker.getStreamMeta()))
    log("MEASURE", ("radio channelStart=%.2fms drift=%.2fms pcmSpread=%dB"):format(
      group.channelStartSkewMs or -1, group.maxAudibleOffsetSpreadMs or -1,
      group.pcmReadBytesSpread or -1))
  end, true)

  actionGate("C5 Radio strict membership snapshot", {
    "Have one extra speaker ready but NOT connected yet.",
    "Radio starts on the current speaker snapshot.",
    "When prompted, connect the extra speaker and press ENTER.",
    "Diagnostics prove it stayed out until the command is rerun.",
  }, function()
    diagReset("radio membership initial")
    local initial = speaker.getSpeakerCount()
    assert(speaker.speakStreamAll(RADIO_URL, 0.40), "strict-membership radio start rejected")
    for i = 1, initial do waitAt(i, "playing", 25, "radio endpoint " .. i) end
    waitTimer(3.0, "sealing radio membership")
    local sealed = diagSnapshot("radio membership sealed")
    local sealedGroup = assert(diagLargestGroup(sealed, "stream:"), "sealed radio diagnostic group missing")
    assert((sealedGroup.sourceCount or 0) == initial, "initial radio diagnostic group count mismatch")

    state.detail = "connect ONE new speaker, then ENTER"
    state.prompt = {"Connect one new speaker now.", "Do NOT rerun the radio command.", "Press ENTER after it is attached."}
    render()
    waitEnter()
    local afterCount = speaker.getSpeakerCount()
    assert(afterCount > initial, "speaker count did not increase")
    waitTimer(3.0, "checking sealed membership")
    local late = diagSnapshot("radio after late speaker")
    local lateGroup = assert(diagLargestGroup(late, "stream:"), "radio group disappeared after late attach")
    assert((lateGroup.sourceCount or 0) == initial,
      "late speaker received a client radio source before the group command was rerun")
    for i = initial + 1, afterCount do
      assert(speaker.audioStatusAt(i).state == "idle", "late speaker joined sealed server radio group")
    end

    safeStop()
    diagReset("radio membership rerun")
    assert(speaker.speakStreamAll(RADIO_URL, 0.40), "radio rerun rejected")
    for i = 1, afterCount do waitAt(i, "playing", 25, "rerun radio endpoint " .. i) end
    waitTimer(4.0, "checking fresh radio membership")
    local rerun = diagSnapshot("radio membership rerun")
    local rerunGroup = assert(diagLargestGroup(rerun, "stream:"), "rerun radio diagnostic group missing")
    assert((rerunGroup.sourceCount or 0) == afterCount,
      ("fresh radio group has %s/%d sources"):format(tostring(rerunGroup.sourceCount), afterCount))
    assert((rerunGroup.playingMembers or 0) == afterCount, "new speaker did not reach PLAYING after rerun")
  end, true)
else
  markSkipped("C4 MP3/ICY group radio (no radio URL supplied)")
  markSkipped("C5 Radio strict membership snapshot (no radio URL supplied)")
end

actionGate("C6 8+ speaker scale stress", {
  "Have enough normal CC:T speakers available to reach 8 total.",
  "The test pauses so you can connect extras now if needed.",
  "It measures 8+ finite sources, then 8+ continuous RAW sources automatically.",
}, function()
  if speaker.getSpeakerCount() < 8 then
    state.detail = "connect speakers until count is at least 8, then ENTER"
    state.prompt = {"Connect extra speakers now.", "Reach at least 8 total.", "Press ENTER."}
    render()
    waitEnter()
  end
  local current = speaker.getSpeakerCount()
  assert(current >= 8, "fewer than 8 speakers are attached")

  diagReset("8+ finite")
  assert(speaker.speakMp3All(mp3, 0.45), "8+ group MP3 rejected")
  verifySharedPlaying()
  waitTimer(15.0, "measuring 8+ finite synchronization")
  local finite = diagSnapshot("8+ finite")
  assertFiniteGroup(finite, current, "8+ finite", true, true)

  safeStop()
  diagReset("8+ RAW")
  local samplesPerChunk = 96000
  local chunks = 3
  local raw = makeRawChunk(samplesPerChunk, 440, 14000)
  for i = 1, chunks do sendRawAll(raw, 0.35, 10) end
  assert(waitUntil(function() return not speaker.speakIsPlaying() end, 10, "waiting for 8+ RAW drain"),
    "8+ RAW server lifetime did not drain")
  local rawSnap = diagSnapshot("8+ RAW", 0.5)
  assertRawGroup(rawSnap, current, samplesPerChunk * 2 * chunks, "8+ RAW")
end, true)

safeStop()
local finalDiagnostics = diagSnapshot("master acceptance complete", 0.2)
dumpSnapshot("master acceptance complete")
speaker.hqDiagEnable(false)

local complete = state.optionalSkipped == 0
state.mode = "DONE"
state.test = "Master acceptance complete"
state.result = complete and "TARGET FULL PASS" or "CORE PASS / TARGET INCOMPLETE"
state.detail = complete and "singleplayer + Sable target passed" or "one or more target-scope checks were skipped"
state.prompt = {
  complete and "TARGET RUNTIME ACCEPTANCE PASSED" or "CORE PASSED - TARGET CHECKS REMAIN",
  ("Runtime %d/%d; target %d/%d; skipped %d"):format(
    state.runtimePassed, state.runtimeTotal, state.optionalPassed, state.optionalTotal, state.optionalSkipped),
  "Log: " .. LOG,
}
render()

log("PASS", "ALL REQUIRED AUTOMATED + REAL-CLIENT DIAGNOSTICS PASSED")
log("INFO", ("runtime=%d/%d target=%d/%d skipped=%d"):format(
  state.runtimePassed, state.runtimeTotal, state.optionalPassed, state.optionalTotal, state.optionalSkipped))
if #state.skippedNames > 0 then
  for _, name in ipairs(state.skippedNames) do log("MISSING", name) end
end

print("")
if complete then
  print("[TARGET FULL PASS] v10 singleplayer + Sable runtime acceptance")
else
  print("[CORE PASS] automatic core passed; target-scope acceptance is INCOMPLETE")
  print("Skipped target checks are listed in " .. LOG)
end
print("Log: " .. LOG)