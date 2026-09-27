-- Standalone C4 finite: 8+ speaker finite catch-up/synchronization proof.
-- Usage: v10_c4_finite <mp3>

local args = {...}
assert(args[1] and not args[2], "usage: v10_c4_finite <mp3>")

local MP3_PATH = args[1]
local LOG = "/v10-c4-finite.log"
local ALIGNMENT_LIMIT_MS = 50
local STARTUP_STALL_LIMIT_MS = 1000
if fs.exists(LOG) then fs.delete(LOG) end

local speaker = assert(peripheral.find("speaker"), "attach a ComputerCraft speaker")
local count = speaker.getSpeakerCount()
assert(count >= 8, "C4 finite requires at least 8 attached speakers")
assert(type(speaker.hqDiagEnable) == "function" and type(speaker.hqDiagSnapshot) == "function",
  "this JAR does not expose the required HQ diagnostics")

local function log(kind, message)
  local line = ("%-8s %s"):format(kind, tostring(message))
  print(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  return data
end

local function safeStop()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
end

local function waitAllPlaying(timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local ready = 0
    for i = 1, count do
      local status = speaker.audioStatusAt(i)
      if status.state == "error" then
        error("endpoint " .. i .. " error: " .. tostring(status.error), 0)
      end
      if status.state == "playing" then ready = ready + 1 end
    end
    if ready == count then return end
    sleep(0.05)
  end
  error("timed out waiting for all " .. count .. " endpoints to play", 0)
end

local function finiteSources(snapshot)
  local out = {}
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == "finite" then out[#out + 1] = source end
  end
  return out
end

local function finiteGroup(snapshot)
  local best = nil
  for _, group in ipairs(snapshot.groups or {}) do
    if tostring(group.group or ""):sub(1, 7) == "finite:" then
      if not best or (group.sourceCount or 0) > (best.sourceCount or 0) then best = group end
    end
  end
  return best
end

local function catchUpSpread(sources)
  local minimum = math.huge
  local maximum = -math.huge
  for i, source in ipairs(sources) do
    assert(source.lastState == "playing", "source " .. i .. " is not PLAYING")
    assert((source.decoderFailures or 0) == 0, "source " .. i .. " had a decoder failure")
    assert((source.playingToStoppedTransitions or 0) == 0,
      "source " .. i .. " stopped unexpectedly during the finite run")
    assert((source.channelStarts or 0) == 1,
      "source " .. i .. " recreated its channel unexpectedly")

    local realBytes = source.pcmReadBytes or 0
    local silenceBytes = source.silenceReadBytes or 0
    local allowedSilence = math.max(32768, math.floor(realBytes * 0.02))
    assert(silenceBytes <= allowedSilence,
      ("source %d inserted %d starvation-silence bytes (limit %d)"):format(
        i, silenceBytes, allowedSilence))

    local channelMs = source.firstChannelMs
    local contentSeconds = source.contentStartSeconds
    assert(type(channelMs) == "number" and channelMs >= 0,
      "source " .. i .. " is missing channel-start timing")
    assert(type(contentSeconds) == "number" and contentSeconds >= 0,
      "source " .. i .. " is missing finite content-start timing")

    local mediaZeroMs = channelMs - contentSeconds * 1000
    minimum = math.min(minimum, mediaZeroMs)
    maximum = math.max(maximum, mediaZeroMs)
  end
  return math.max(0, maximum - minimum)
end

local mp3 = readBinary(MP3_PATH)

local ok, err = pcall(function()
  speaker.hqDiagEnable(true)
  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", "epoch=" .. tostring(epoch) .. " speakers=" .. count)

  assert(speaker.speakMp3All(mp3, 0.45), "8+ group MP3 rejected")
  waitAllPlaying(15)
  sleep(10)

  local snap = speaker.hqDiagSnapshot()
  local sources = finiteSources(snap)
  assert(#sources == count, ("expected %d finite sources, got %d"):format(count, #sources))

  local group = assert(finiteGroup(snap), "finite diagnostic group missing")
  assert((group.sourceCount or 0) == count,
    ("finite group has %s/%d sources"):format(tostring(group.sourceCount), count))
  assert((group.playingMembers or 0) == count,
    ("finite group has %s/%d PLAYING sources"):format(tostring(group.playingMembers), count))

  local startup = group.channelStartSkewMs
  assert(type(startup) == "number" and startup >= 0, "channel-start spread missing")
  assert(startup <= STARTUP_STALL_LIMIT_MS,
    ("endpoint startup spread %.2f ms looks like a real stall (limit %.0f ms)"):format(
      startup, STARTUP_STALL_LIMIT_MS))

  local alignment = catchUpSpread(sources)
  log("MEASURE", ("channelStart=%.2fms catchUpAlignment=%.2fms"):format(startup, alignment))
  assert(alignment <= ALIGNMENT_LIMIT_MS,
    ("finite catch-up alignment %.2f ms exceeds %.0f ms"):format(alignment, ALIGNMENT_LIMIT_MS))

  log("PASS", "C4 finite 8+ catch-up synchronization")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
