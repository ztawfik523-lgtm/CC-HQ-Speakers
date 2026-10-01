-- CC:HQ Speakers v11 integrated runtime test 2.
-- Covers: exactly-8 finite scale/catch-up, Sable movement, exactly-8 RAW continuation/backpressure,
-- and exactly-8 MP3/ICY radio smoke + metadata under v11 tuning fields.
-- Requires exactly 8 attached speakers on the Sable/Aeronautics setup.
-- Usage: v11_runtime_2 <mp3> [radio-url]

local args = {...}
assert(args[1] and not args[3], "usage: v11_runtime_2 <mp3> [radio-url]")

local MP3_PATH = args[1]
local RADIO_URL = args[2] or "https://stream.nightride.fm/nightride.mp3"
local LOG = "/v11-runtime-2.log"
local COUNT = 8
local STARTUP_STALL_LIMIT_MS = 1000
local ALIGNMENT_LIMIT_MS = 50
local RAW_START_SKEW_LIMIT_MS = 75
local STREAM_DRIFT_LIMIT_MS = 100
local EPS = 0.002

if fs.exists(LOG) then fs.delete(LOG) end
local speaker = assert(peripheral.find("speaker"), "attach ComputerCraft speakers")

local function waitSpeakerCount(expected, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  local count = speaker.getSpeakerCount()
  while count ~= expected and os.epoch("utc") < deadline do
    sleep(0.10)
    count = speaker.getSpeakerCount()
  end
  if count ~= expected then
    local ok, members = pcall(function()
      return textutils.serialize(speaker.getSpeakers(), {compact = true})
    end)
    error(("test 2 requires exactly %d HQ-grouped speakers; HQ count=%d members=%s"):format(
      expected, count, ok and members or "<unavailable>"), 0)
  end
  return count
end

waitSpeakerCount(COUNT, 5)
assert(type(speaker.hqDiagEnable) == "function" and type(speaker.hqDiagSnapshot) == "function",
  "this JAR does not expose the required HQ diagnostics")

local function log(kind, message)
  local line = ("%s %-10s %s"):format(tostring(os.epoch("utc")), kind, tostring(message))
  print(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
end

local function serialize(v)
  local ok, out = pcall(textutils.serialize, v, {compact = true})
  return ok and out or tostring(v)
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  log("ASSET", path .. " bytes=" .. #data)
  return data
end

local function safeStop()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function waitEnter()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "key" and e[2] == keys.enter then return end
  end
end

local function prompt(lines)
  print("")
  for _, line in ipairs(lines) do print(line) end
end

local function approx(actual, expected, label, eps)
  eps = eps or EPS
  assert(type(actual) == "number", label .. " is not numeric")
  assert(math.abs(actual - expected) <= eps,
    ("%s expected %.4f, got %.4f"):format(label, expected, actual))
end

local function assertTuning(status, label)
  assert(status.state == "playing", label .. ": state=" .. tostring(status.state))
  approx(status.volume, 1.5, label .. " volume")
  approx(status.gain, 0.50, label .. " gain")
  approx(status.range, 48.0, label .. " range", 0.02)
  assert(status.rangeMode == "auto", label .. ": rangeMode=" .. tostring(status.rangeMode))
end

local function waitAllPlaying(timeout, label)
  local deadline = os.epoch("utc") + timeout * 1000
  local playbackId = nil
  while os.epoch("utc") < deadline do
    local ready = 0
    local id = nil
    for i = 1, COUNT do
      local s = speaker.audioStatusAt(i)
      if s.state == "error" then error(label .. " endpoint " .. i .. ": " .. tostring(s.error), 0) end
      if s.state == "playing" then
        ready = ready + 1
        id = id or s.playbackId
        if s.playbackId and id then assert(s.playbackId == id, label .. ": playbackId mismatch") end
      end
    end
    if ready == COUNT then return id end
    sleep(0.05)
  end
  error(label .. ": timed out waiting for all 8 endpoints", 0)
end

local function waitAllIdle(timeout, label)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local idle = 0
    for i = 1, COUNT do
      local s = speaker.audioStatusAt(i)
      if s.state == "error" then error(label .. " endpoint " .. i .. ": " .. tostring(s.error), 0) end
      if s.state == "idle" then idle = idle + 1 end
    end
    if idle == COUNT then return end
    sleep(0.05)
  end
  error(label .. ": timed out waiting for all 8 endpoints to become idle", 0)
end

local function waitUntil(predicate, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    if predicate() then return true end
    sleep(0.10)
  end
  return predicate()
end

local function diagReset(label)
  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", label .. " epoch=" .. tostring(epoch))
end

local function diagSnapshot(label, settle)
  if settle and settle > 0 then sleep(settle) end
  local snap = speaker.hqDiagSnapshot()
  log("DIAG", label .. " captured")
  return snap
end

local function diagSources(snapshot, kind)
  local out = {}
  for _, source in ipairs(snapshot.sources or {}) do
    if not kind or source.kind == kind then out[#out + 1] = source end
  end
  return out
end

local function largestGroup(snapshot, prefix)
  local best = nil
  for _, group in ipairs(snapshot.groups or {}) do
    if tostring(group.group or ""):sub(1, #prefix) == prefix then
      if not best or (group.sourceCount or 0) > (best.sourceCount or 0) then best = group end
    end
  end
  return best
end

local function assertSourceHealthy(source, label, continuous)
  assert(source, label .. ": source missing")
  assert((source.channelStarts or 0) >= 1, label .. ": no real client channel")
  assert((source.playingSamples or 0) >= 3, label .. ": insufficient PLAYING samples")
  assert((source.decoderFailures or 0) == 0, label .. ": decoder failure")
  if continuous then
    assert((source.playingToStoppedTransitions or 0) == 0, label .. ": unexpected PLAYING->STOPPED transition")
  end
end

local function finiteCatchUpSpread(sources)
  local minimum, maximum = math.huge, -math.huge
  assert(#sources == COUNT, "finite source count mismatch")
  for i, source in ipairs(sources) do
    assertSourceHealthy(source, "finite source " .. i, true)
    assert(source.lastState == "playing", "finite source " .. i .. " is not PLAYING")
    local channelMs = source.firstChannelMs
    local contentSeconds = source.contentStartSeconds
    assert(type(channelMs) == "number" and channelMs >= 0, "finite source missing channel-start timing")
    assert(type(contentSeconds) == "number" and contentSeconds >= 0, "finite source missing content-start timing")
    local mediaZeroMs = channelMs - contentSeconds * 1000
    minimum = math.min(minimum, mediaZeroMs)
    maximum = math.max(maximum, mediaZeroMs)
  end
  return math.max(0, maximum - minimum)
end

local function currentAudibleSpreadMs(snapshot, groupName, kind)
  local minimum, maximum, count = math.huge, -math.huge, 0
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == kind and source.group == groupName and source.lastState == "playing" then
      local logical = source.lastLogicalSeconds
      local latency = source.lastOutputLatencySeconds or 0
      if type(logical) == "number" and logical >= 0 and type(latency) == "number" then
        local audible = logical - math.max(0, latency)
        minimum = math.min(minimum, audible)
        maximum = math.max(maximum, audible)
        count = count + 1
      end
    end
  end
  assert(count == COUNT, ("current sync sample has %d/%d usable %s sources"):format(count, COUNT, kind))
  return math.max(0, (maximum - minimum) * 1000)
end

local function assertSettledSync(kind, prefix, limitMs, startLimitMs, label)
  local consecutive = 0
  local lastSpread = math.huge
  for sample = 1, 8 do
    local snap = diagSnapshot(label .. " sample " .. sample)
    local sources = diagSources(snap, kind)
    assert(#sources == COUNT, ("%s: expected %d sources, got %d"):format(label, COUNT, #sources))
    for i, src in ipairs(sources) do
      assertSourceHealthy(src, label .. " source " .. i, true)
      assert(src.lastState == "playing", label .. " source " .. i .. " not PLAYING")
    end
    local group = assert(largestGroup(snap, prefix), label .. ": diagnostic group missing")
    assert((group.sourceCount or 0) == COUNT and (group.playingMembers or 0) == COUNT,
      label .. ": group member count mismatch")
    if startLimitMs then
      assert(type(group.channelStartSkewMs) == "number" and group.channelStartSkewMs <= startLimitMs,
        ("%s channel start skew %.2f ms exceeds %.0f ms"):format(label, group.channelStartSkewMs or -1, startLimitMs))
    end
    local spread = currentAudibleSpreadMs(snap, group.group, kind)
    lastSpread = spread
    if spread <= limitMs then consecutive = consecutive + 1 else consecutive = 0 end
    if consecutive >= 3 then
      log("MEASURE", ("%s channelStart=%.2fms settledDrift=%.2fms"):format(label, group.channelStartSkewMs or -1, spread))
      return snap, sources, group
    end
    sleep(0.20)
  end
  error(("%s did not settle within %.0f ms; last %.2f ms"):format(label, limitMs, lastSpread), 0)
end

local function makeRawChunk(samples, frequency, amplitude)
  local chunk = {}
  local step = 2 * math.pi * frequency / 48000
  for i = 1, samples do chunk[i] = math.floor(math.sin(i * step) * amplitude) end
  return chunk
end

local function sendRawAll(chunk, volume, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while true do
    if speaker.speakPCMAll(chunk, volume) then return end
    if os.epoch("utc") >= deadline then error("timed out waiting for RAW backpressure capacity", 0) end
    local timer = os.startTimer(0.5)
    while true do
      local e = {os.pullEventRaw()}
      if e[1] == "terminate" then error("terminated", 0) end
      if e[1] == "hqspeaker_audio_empty" then break end
      if e[1] == "timer" and e[2] == timer then break end
    end
  end
end

local function run(name, fn)
  log("BEGIN", name)
  local ok, err = pcall(fn)
  if not ok then
    log("FAIL", name .. ": " .. tostring(err))
    pcall(function() log("DIAGFAIL", serialize(speaker.hqDiagSnapshot())) end)
    safeStop()
    error(err, 0)
  end
  safeStop()
  log("PASS", name)
end

local mp3 = readBinary(MP3_PATH)
log("SETUP", "speakers=" .. serialize(speaker.getSpeakers()))
log("RADIO", RADIO_URL)
speaker.hqDiagEnable(true)

run("8-SPEAKER FINITE + SABLE MOVEMENT", function()
  diagReset("8 finite")
  assert(speaker.speakMp3All(mp3, 1.5), "8-speaker finite start rejected")
  local playbackId = waitAllPlaying(15, "8 finite")
  assert(speaker.audioSetLoopingAll(true), "8 finite loop enable failed")
  for i = 1, COUNT do assertTuning(speaker.audioStatusAt(i), "finite endpoint " .. i) end

  sleep(3.0)
  local baseline = diagSnapshot("8 finite baseline")
  local finite = diagSources(baseline, "finite")
  local group = assert(largestGroup(baseline, "finite:"), "finite diagnostic group missing")
  assert((group.sourceCount or 0) == COUNT and (group.playingMembers or 0) == COUNT,
    "finite diagnostic group does not contain 8 PLAYING sources")
  assert(type(group.channelStartSkewMs) == "number" and group.channelStartSkewMs <= STARTUP_STALL_LIMIT_MS,
    ("finite endpoint startup spread %.2f ms looks like a stall"):format(group.channelStartSkewMs or -1))
  local alignment = finiteCatchUpSpread(finite)
  assert(alignment <= ALIGNMENT_LIMIT_MS,
    ("finite catch-up alignment %.2f ms exceeds %.0f ms"):format(alignment, ALIGNMENT_LIMIT_MS))
  log("MEASURE", ("finite channelStart=%.2fms catchUpAlignment=%.2fms"):format(group.channelStartSkewMs, alignment))

  prompt({
    "SABLE MOVEMENT PHASE",
    "The same 8-speaker looping playback is still running.",
    "Move AND rotate the Sable/Aeronautics contraption by clearly more than 1 block.",
    "Keep it moving for several seconds, then park it and return to this computer.",
    "Press ENTER after movement is complete. Diagnostics judge movement and recovery.",
  })
  waitEnter()
  sleep(0.75)
  local moved = diagSnapshot("Sable moved")
  local movedSources = diagSources(moved, "finite")
  assert(#movedSources == COUNT, "Sable finite source count changed")
  for i, source in ipairs(movedSources) do
    assertSourceHealthy(source, "Sable source " .. i, false)
    assert(source.lastState == "playing", "Sable source " .. i .. " did not recover to PLAYING")
    assert((source.requestedMovement or 0) >= 1.0,
      ("Sable source %d requested movement only %.3f blocks"):format(i, source.requestedMovement or 0))
    assert((source.actualMovement or 0) >= 1.0,
      ("Sable source %d actual OpenAL movement only %.3f blocks"):format(i, source.actualMovement or 0))
    assert((source.decoderFailures or 0) == 0, "Sable source " .. i .. " decoder failure")
    log("MOVE", ("source%d requested=%.3f actual=%.3f finalDelta=%.3f maxDelta=%.3f"):format(
      i, source.requestedMovement or 0, source.actualMovement or 0,
      source.lastPositionError or -1, source.maxPositionError or 0))
  end
  for i = 1, COUNT do
    local s = speaker.audioStatusAt(i)
    assert(s.state == "playing" and s.playbackId == playbackId, "Sable movement changed finite playback authority")
  end
end)

run("8-SPEAKER RAW CONTINUATION + BACKPRESSURE", function()
  diagReset("8 RAW")
  local samplesPerChunk = 96000
  local chunks = 3
  local expectedBytes = samplesPerChunk * 2 * chunks
  local raw = makeRawChunk(samplesPerChunk, 440, 14000)
  for i = 1, chunks do
    sendRawAll(raw, 1.5, 12)
    log("RAW", "accepted 2-second chunk " .. i .. "/" .. chunks)
  end
  sleep(0.75)

  for i = 1, COUNT do assertTuning(speaker.audioStatusAt(i), "RAW endpoint " .. i) end
  local active = diagSnapshot("8 RAW active")
  local sources = diagSources(active, "raw")
  assert(#sources == COUNT, ("expected 8 RAW client sources, got %d"):format(#sources))
  for i, source in ipairs(sources) do
    assertSourceHealthy(source, "RAW source " .. i, true)
    assert(source.lastState == "playing", "RAW source " .. i .. " not PLAYING while admitted audio remains")
    assert((source.pcmInputBytes or 0) == expectedBytes,
      ("RAW source %d admitted %d/%d bytes"):format(i, source.pcmInputBytes or -1, expectedBytes))
    assert((source.channelStarts or 0) == 1, "RAW source " .. i .. " recreated its channel between chunks")
  end
  assertSettledSync("raw", "raw:", 50, RAW_START_SKEW_LIMIT_MS, "8 RAW")

  waitAllIdle(12, "8 RAW drain")
  local done = diagSnapshot("8 RAW drained", 0.5)
  local final = diagSources(done, "raw")
  assert(#final == COUNT, "RAW diagnostic history lost sources after drain")
  for i, source in ipairs(final) do
    assert((source.pcmInputBytes or 0) == expectedBytes, "RAW source " .. i .. " input byte mismatch")
    assert((source.pcmReadBytes or 0) >= expectedBytes - 8192,
      ("RAW source %d delivered only %d/%d bytes to OpenAL"):format(i, source.pcmReadBytes or -1, expectedBytes))
    assert((source.channelStarts or 0) == 1, "RAW source " .. i .. " recreated its channel")
    assert((source.decoderFailures or 0) == 0, "RAW source " .. i .. " decoder failure")
  end
end)

run("8-SPEAKER MP3/ICY RADIO + METADATA", function()
  diagReset("8 radio")
  local serialBefore = speaker.getStreamMetaSerial()
  assert(speaker.speakStreamAll(RADIO_URL, 1.5), "radio URL rejected: " .. RADIO_URL)
  waitAllPlaying(25, "8 radio")
  assert(speaker.isStreaming(), "radio server ownership did not remain active")
  assert(speaker.getStreamUrl() == RADIO_URL, "active radio URL getter mismatch")
  for i = 1, COUNT do assertTuning(speaker.audioStatusAt(i), "radio endpoint " .. i) end

  local metadataSeen = waitUntil(function()
    return speaker.getStreamMetaSerial() > serialBefore
  end, 30)
  assert(metadataSeen, "no ICY metadata arrived within 30 seconds")
  sleep(5.0)

  local snap, sources, group = assertSettledSync("stream", "stream:", STREAM_DRIFT_LIMIT_MS, 150, "8 radio")
  assert((group.sourceCount or 0) == COUNT and (group.playingMembers or 0) == COUNT,
    "radio group does not contain 8 PLAYING sources")
  for i, source in ipairs(sources) do
    assert((source.pcmReadBytes or 0) > 100000, "radio source " .. i .. " did not deliver meaningful decoded PCM")
    assert((source.decoderFailures or 0) == 0, "radio source " .. i .. " decoder failure")
  end

  local meta = speaker.getStreamMeta()
  assert(type(meta) == "table" and (meta.serial or 0) > serialBefore, "radio metadata serial did not advance")
  local useful = tostring(meta.title or "") ~= "" or tostring(meta.station or "") ~= ""
    or tostring(meta.song or "") ~= "" or tostring(meta.genre or "") ~= ""
  assert(useful, "ICY metadata event arrived but contained no title/station/song/genre")
  log("META", serialize(meta))
  log("RADIO", "sources=" .. #diagSources(snap, "stream") .. " group=" .. tostring(group.group))
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)
log("PASS", "V11 RUNTIME TEST 2 COMPLETE")
print("")
print("[PASS] v11 runtime test 2")
print("Log: " .. LOG)