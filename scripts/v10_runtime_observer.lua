-- v10 manual-runtime observer with monitor dashboard and persistent diagnostics.
-- Usage: v10_runtime_observer [seconds]
-- Run while performing walk-in/out, F3+T/resource reload, Sable/VS2 movement,
-- radio membership, or other manual phases.

local seconds = tonumber(({...})[1]) or 180
assert(seconds > 0 and seconds <= 1800, "seconds must be 1..1800")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local monitor = peripheral.find("monitor")
local LOG_PATH = "/v10-runtime-observer.log"
local startedMs = os.epoch("utc")
local finishTimer = os.startTimer(seconds)
local pollTimer = os.startTimer(0.25)
local lastStatus = ""
local lastEvent = "-"
local stateChanges = 0
local eventCount = 0
local tail = {}

local interesting = {
  hqspeaker_audio_state = true,
  hqspeaker_audio_empty = true,
  hqspeaker_metadata = true,
  speaker_audio_empty = true,
  peripheral = true,
  peripheral_detach = true,
}

local function shorten(value, limit)
  local s = tostring(value or "")
  if #s <= limit then return s end
  return s:sub(1, math.max(1, limit - 3)) .. "..."
end

local function serialize(value)
  local ok, encoded = pcall(textutils.serialize, value, {compact = true})
  return ok and encoded or tostring(value)
end

local function elapsed()
  return (os.epoch("utc") - startedMs) / 1000
end

local function remaining()
  return math.max(0, seconds - elapsed())
end

local function appendTail(line)
  tail[#tail + 1] = line
  while #tail > 8 do table.remove(tail, 1) end
end

local function log(kind, message)
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

local function render(statusText)
  if not monitor then return end
  local ok = pcall(function()
    monitor.setTextScale(0.5)
    local w, h = monitor.getSize()
    monitor.setCursorBlink(false)
    monitor.clear()
    writeAt(monitor, 1, "CC:HQ Speakers - v10 Observer")
    writeAt(monitor, 2, ("Elapsed %.1fs   Remaining %.1fs"):format(elapsed(), remaining()))
    writeAt(monitor, 3, ("Speakers %d   State changes %d   Events %d")
      :format(speaker.getSpeakerCount(), stateChanges, eventCount))
    writeAt(monitor, 4, "Status: " .. shorten(statusText or lastStatus, math.max(12, w - 8)))
    writeAt(monitor, 5, "Event: " .. shorten(lastEvent, math.max(12, w - 7)))
    writeAt(monitor, 6, "Log: " .. LOG_PATH)
    if h >= 8 then
      writeAt(monitor, 7, string.rep("-", math.max(1, math.min(w, 40))))
      local first = math.max(1, #tail - (h - 8))
      local y = 8
      for i = first, #tail do
        if y > h then break end
        writeAt(monitor, y, tail[i])
        y = y + 1
      end
    end
  end)
  if not ok then monitor = nil end
end

do
  local h = fs.open(LOG_PATH, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 runtime observer diagnostics")
    h.writeLine("Started UTC ms: " .. tostring(startedMs))
    h.writeLine("Duration seconds: " .. tostring(seconds))
    h.writeLine("Computer ID: " .. tostring(os.getComputerID()))
    h.writeLine("OS: " .. tostring(os.version()))
    h.writeLine("Speaker count: " .. tostring(speaker.getSpeakerCount()))
    h.writeLine("Speakers: " .. serialize(speaker.getSpeakers()))
    h.close()
  end
end

log("BEGIN", "observer for " .. seconds .. " seconds")
log("INFO", "speakers = " .. serialize(speaker.getSpeakers()))
log("INFO", "monitor = " .. (monitor and tostring(peripheral.getName(monitor)) or "none"))

local function sampleStatus()
  local ok, status = pcall(function() return speaker.audioStatus() end)
  local encoded = ok and serialize(status) or ("<status error: " .. tostring(status) .. ">")
  if encoded ~= lastStatus then
    stateChanges = stateChanges + 1
    lastStatus = encoded
    log("STATE", encoded)
  end
  render(encoded)
end

sampleStatus()

while true do
  local e = {os.pullEventRaw()}
  if e[1] == "terminate" then
    log("STOP", "terminated by user")
    error("terminated", 0)
  end

  if e[1] == "timer" and e[2] == finishTimer then
    break
  end

  if e[1] == "timer" and e[2] == pollTimer then
    sampleStatus()
    pollTimer = os.startTimer(0.25)
  elseif interesting[e[1]] then
    eventCount = eventCount + 1
    lastEvent = serialize(e)
    log("EVENT", lastEvent)
    render(lastStatus)
  end
end

sampleStatus()
log("PASS", ("observer completed; %d state changes, %d interesting events"):format(stateChanges, eventCount))
render(lastStatus)
print("[PASS] observer completed")
print("Diagnostic log: " .. LOG_PATH)
