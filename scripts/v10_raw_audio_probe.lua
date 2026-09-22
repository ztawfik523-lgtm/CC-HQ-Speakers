-- CC:HQ Speakers v10 RAW audible isolation probe.
-- Requires at least 2 attached speakers.
--
-- Tests:
--   1) one 2-second HQ RAW chunk on one speaker
--   2) one 2-second HQ RAW chunk on all speakers
--   3) three paced 2-second chunks = ~6 seconds continuous on all speakers
--
-- P = pass, R = replay, F = fail. Results are logged to /v10-raw-probe.log.

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
assert(speaker.getSpeakerCount() >= 2, "attach at least 2 speakers")

local LOG = "/v10-raw-probe.log"
local started = os.epoch("utc")

local function elapsed()
  return (os.epoch("utc") - started) / 1000
end

local function log(kind, msg)
  local line = ("[%7.2fs] %-7s %s"):format(elapsed(), kind, tostring(msg))
  print(line)
  local h = fs.open(LOG, "a")
  if h then
    h.writeLine(line)
    h.close()
  end
end

do
  local h = fs.open(LOG, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 RAW audible isolation probe")
    h.writeLine("Speaker count: " .. tostring(speaker.getSpeakerCount()))
    h.writeLine("Speakers: " .. textutils.serialize(speaker.getSpeakers(), {compact=true}))
    h.close()
  end
end

local function stopAll()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function waitSeconds(seconds)
  local timer = os.startTimer(seconds)
  while true do
    local e, a = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "timer" and a == timer then return end
  end
end

local function waitCapacity(timeout)
  local timer = os.startTimer(timeout)
  while true do
    local e, a = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "hqspeaker_audio_empty" then
      os.cancelTimer(timer)
      log("EVENT", "hqspeaker_audio_empty")
      return
    end
    if e == "timer" and a == timer then
      error("timed out waiting for hqspeaker_audio_empty", 0)
    end
  end
end

local function choice()
  while true do
    local e, c = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "char" then
      c = string.lower(c)
      if c == "p" or c == "r" or c == "f" then return c end
    end
  end
end

local function enter()
  while true do
    local e, k = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "key" and k == keys.enter then return end
  end
end

local function gate(name, expected, fn)
  while true do
    stopAll()
    print("")
    print("=== " .. name .. " ===")
    print(expected)
    print("Press ENTER when ready.")
    enter()

    log("START", name)
    fn()
    stopAll()

    print("Result: [P]ass  [R]eplay  [F]ail")
    local c = choice()
    if c == "p" then
      log("RESULT", name .. " = PASS")
      return true
    elseif c == "f" then
      log("RESULT", name .. " = FAIL")
      return false
    else
      log("RESULT", name .. " = REPLAY")
    end
  end
end

assert(speaker.speakSampleRate() == 48000, "unexpected RAW sample rate")
assert(speaker.speakMaxSamples() >= 96000, "RAW max sample count too small")

local chunk = {}
local step = 2 * math.pi * 440 / 48000
for i = 1, 96000 do
  chunk[i] = math.floor(math.sin(i * step) * 15000)
end

local ok1 = gate(
  "1/3 Single speaker, single chunk",
  "Expect ONE speaker to play a steady tone for about 2 seconds.",
  function()
    assert(speaker.speakPCM(chunk, 0.45), "speakPCM rejected")
    waitSeconds(3.0)
  end
)
if not ok1 then
  error("RAW probe failed test 1; send " .. LOG, 0)
end

local ok2 = gate(
  "2/3 All speakers, single chunk",
  "Expect BOTH speakers together for about 2 seconds.",
  function()
    assert(speaker.speakPCMAll(chunk, 0.45), "speakPCMAll rejected")
    waitSeconds(3.0)
  end
)
if not ok2 then
  error("RAW probe failed test 2; send " .. LOG, 0)
end

local ok3 = gate(
  "3/3 All speakers, continuous feed",
  "Expect BOTH speakers continuously for about 6 seconds with no early cutoff.",
  function()
    for i = 1, 3 do
      while not speaker.speakPCMAll(chunk, 0.45) do
        log("WAIT", "chunk " .. i .. " waiting for capacity")
        waitCapacity(8)
      end
      log("QUEUE", "accepted chunk " .. i .. "/3")
    end
    waitSeconds(4.0)
  end
)
if not ok3 then
  error("RAW probe failed test 3; send " .. LOG, 0)
end

log("PASS", "ALL RAW AUDIBLE PROBE TESTS PASSED")
print("")
print("[PASS] RAW audible isolation probe")
print("Log: " .. LOG)
