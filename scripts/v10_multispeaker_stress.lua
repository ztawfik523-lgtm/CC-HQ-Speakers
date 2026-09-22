-- CC:HQ Speakers v10 Phase 1 multispeaker stress + audible sync acceptance.
-- Usage: v10_multispeaker_stress <mp3> [cycles]
--
-- Part A: silent mechanical/control stress.
-- Part B: human audible sync/stopAt/continuous-RAW checks.
--
-- Final PASS requires both automated and human gates.

local args = {...}
assert(args[1], "usage: v10_multispeaker_stress <mp3> [cycles]")
local cycles = tonumber(args[2]) or 8
assert(cycles >= 1 and cycles <= 50, "cycles must be 1..50")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local monitor = peripheral.find("monitor")
local count = speaker.getSpeakerCount()
assert(count >= 2, "attach at least two speakers to this computer")

local LOG = "/v10-phase1.log"
local started = os.epoch("utc")
local state = {
  mode = "AUTO",
  test = "starting",
  detail = "",
  auto = 0,
  autoTotal = cycles,
  listen = 0,
  listenTotal = 3,
  result = "RUNNING",
}

local function elapsed()
  return (os.epoch("utc") - started) / 1000
end

local function log(kind, msg)
  local line = ("[%7.2fs] %-7s %s"):format(elapsed(), kind, tostring(msg))
  print(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
end

local function short(s, n)
  s = tostring(s or "")
  return #s <= n and s or s:sub(1, math.max(1, n - 3)) .. "..."
end

local function render(extra)
  if not monitor then return end
  local ok = pcall(function()
    monitor.setTextScale(0.5)
    monitor.clear()
    local w, h = monitor.getSize()
    local function row(y, text)
      if y > h then return end
      monitor.setCursorPos(1, y)
      monitor.clearLine()
      monitor.write(short(text, w))
    end
    row(1, "CC:HQ Speakers - Phase 1")
    row(2, ("Mode %s | %s"):format(state.mode, state.result))
    row(3, ("Auto %d/%d | Listen %d/%d | Speakers %d")
      :format(state.auto, state.autoTotal, state.listen, state.listenTotal, count))
    row(4, "Test: " .. state.test)
    row(5, "Now: " .. state.detail)
    if extra then
      for i, line in ipairs(extra) do row(5 + i, line) end
    end
    row(math.min(h, 10), "Log: " .. LOG)
  end)
  if not ok then monitor = nil end
end

local function timer(seconds, detail)
  state.detail = detail or state.detail
  render()
  local id = os.startTimer(seconds)
  while true do
    local e, a = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "timer" and a == id then return end
  end
end

local function waitAt(index, wanted, timeout)
  local deadline = os.startTimer(timeout)
  local poll = os.startTimer(0.05)
  while true do
    local s = speaker.audioStatusAt(index)
    if s.state == wanted then
      os.cancelTimer(deadline)
      os.cancelTimer(poll)
      return s
    end
    if s.state == "error" then
      error("endpoint " .. index .. " error: " .. tostring(s.error), 0)
    end

    local e, a = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "timer" and a == deadline then
      error(("timeout endpoint %d -> %s"):format(index, wanted), 0)
    end
    if e == "timer" and a == poll then poll = os.startTimer(0.05) end
  end
end

local function stopAll()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.speakStop() end)
  pcall(function() speaker.stop() end)
end

local function verifyShared()
  local first = waitAt(1, "playing", 15)
  local id = first.playbackId
  for i = 2, count do
    local s = waitAt(i, "playing", 15)
    if id and s.playbackId then
      assert(s.playbackId == id, "playbackId mismatch at endpoint " .. i)
    end
  end
  return first
end

local function waitEnter()
  while true do
    local e, k = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "key" and k == keys.enter then return end
  end
end

local function verdict()
  while true do
    local e, c = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "char" then
      c = string.lower(c)
      if c == "p" or c == "r" or c == "f" then return c end
    end
  end
end

local function gate(name, instructions, fn)
  while true do
    stopAll()
    state.mode = "LISTEN"
    state.test = name
    state.detail = "Press ENTER when ready"
    render(instructions)

    print("")
    print("=== " .. name .. " ===")
    for _, line in ipairs(instructions) do print(line) end
    print("Press ENTER when ready.")
    waitEnter()

    state.detail = "LISTEN NOW"
    render(instructions)
    log("LISTEN", name .. " started")

    local ok, err = pcall(fn)
    stopAll()
    if not ok then
      state.result = "FAIL"
      render({tostring(err)})
      log("FAIL", name .. ": " .. tostring(err))
      error("Phase 1 failed: " .. tostring(err) .. "\nLog: " .. LOG, 0)
    end

    state.detail = "P=pass  R=replay  F=fail"
    render({"Judge what you just heard.", "P=PASS  R=REPLAY  F=FAIL"})
    print("Result: [P]ass  [R]eplay  [F]ail")
    local c = verdict()

    if c == "p" then
      state.listen = state.listen + 1
      log("MANUAL", name .. " = PASS")
      return
    elseif c == "r" then
      log("MANUAL", name .. " = REPLAY")
    else
      state.result = "FAIL"
      render({"User marked this audible gate failed."})
      log("MANUAL", name .. " = FAIL")
      error("Phase 1 audible failure in " .. name .. "\nLog: " .. LOG, 0)
    end
  end
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  return data
end

local function makeRawChunk()
  local chunk = {}
  local step = 2 * math.pi * 440 / 48000
  for i = 1, 96000 do
    chunk[i] = math.floor(math.sin(i * step) * 15000)
  end
  return chunk
end

local function waitRawCapacity(timeout)
  local deadline = os.startTimer(timeout)
  while true do
    local e, a = os.pullEventRaw()
    if e == "terminate" then error("terminated", 0) end
    if e == "hqspeaker_audio_empty" then
      os.cancelTimer(deadline)
      return
    end
    if e == "timer" and a == deadline then
      error("timed out waiting for RAW capacity", 0)
    end
  end
end

local mp3 = readBinary(args[1])

do
  local h = fs.open(LOG, "w")
  if h then
    h.writeLine("CC:HQ Speakers v10 Phase 1 multispeaker stress")
    h.writeLine("Speakers: " .. textutils.serialize(speaker.getSpeakers(), {compact=true}))
    h.writeLine("Cycles: " .. cycles)
    h.close()
  end
end

log("INFO", ("%d speakers, %d stress cycles"):format(count, cycles))
log("INFO", "positions = " .. textutils.serialize(speaker.getSpeakers(), {compact=true}))

-- Part A: silent control/supersession stress.
state.mode = "AUTO"
for cycle = 1, cycles do
  state.test = ("Control stress %d/%d"):format(cycle, cycles)
  state.detail = "silent group/control race"
  render()

  speaker.audioStopAll()
  assert(speaker.speakMp3All(mp3, 0.0), "group start rejected at cycle " .. cycle)
  local status = verifyShared()
  local seek = math.min(0.5, math.max(0, (status.duration or 1) * 0.1))

  parallel.waitForAll(
    function() speaker.audioSeek(seek) end,
    function() speaker.audioSetVolumeAt(1, 0.0) end,
    function() speaker.audioSetMutedAt(2, cycle % 2 == 0) end,
    function() speaker.audioSetLooping(cycle % 2 == 0) end
  )

  assert(speaker.audioSetLooping(false), "post-race loop stabilization failed")
  assert(speaker.audioSetMutedAt(2, false), "post-race unmute failed")
  assert(speaker.audioSetVolumeAt(1, 0.0), "post-race volume stabilization failed")

  local s1 = speaker.audioStatusAt(1)
  local s2 = speaker.audioStatusAt(2)
  assert(s1.state == "playing" or s1.state == "paused", "endpoint 1 corrupted after race")
  assert(s2.state == "playing" or s2.state == "paused", "endpoint 2 corrupted after race")

  if cycle % 2 == 0 then
    speaker.audioStopAt(2)
    waitAt(2, "idle", 5)
    local survivor = speaker.audioStatusAt(1)
    assert(survivor.state == "playing" or survivor.state == "paused",
      "audioStopAt(2) killed endpoint 1 at cycle " .. cycle)
  end

  speaker.audioStopAll()
  for i = 1, count do waitAt(i, "idle", 5) end
  state.auto = cycle
  log("PASS", ("stress cycle %d/%d"):format(cycle, cycles))
  render()
end

-- Part B: audible acceptance.
gate("L1/3 Group MP3 sync", {
  "Stand roughly equally far from all speakers.",
  "All speakers play the same MP3 for 12 seconds.",
  "PASS only if it sounds like one synchronized source.",
  "FAIL for echo, drift, staggered start, or dropouts.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.60), "speakMp3All rejected")
  verifyShared()
  timer(12.0, "all speakers should remain synchronized")
end)

local p2 = speaker.getSpeakerPos(2)
gate("L2/3 Endpoint-local stop", {
  "All speakers play together for 6 seconds.",
  ("Then speaker #2 at %.0f,%.0f,%.0f stops."):format(p2.x, p2.y, p2.z),
  "Every other speaker must continue for 6 seconds.",
}, function()
  assert(speaker.speakMp3All(mp3, 0.60), "speakMp3All rejected")
  verifyShared()
  timer(6.0, "all speakers playing")

  speaker.audioStopAt(2)
  waitAt(2, "idle", 5)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing" or survivor.state == "paused",
    "audioStopAt(2) killed endpoint 1")

  timer(6.0, "speaker #2 stopped; others continue")
end)

gate("L3/3 Continuous RAW sync", {
  "This rechecks the RAW continuation fix.",
  "All speakers should play one continuous 440 Hz tone",
  "for about 6 seconds.",
  "FAIL for early cutoff, gaps, echo/desync, or crackle.",
}, function()
  local chunk = makeRawChunk()
  for i = 1, 3 do
    while not speaker.speakPCMAll(chunk, 0.45) do
      waitRawCapacity(8)
    end
    log("RAW", "accepted 2-second chunk " .. i .. "/3")
  end
  timer(4.0, "allowing final buffered RAW to finish")
end)

stopAll()
state.mode = "DONE"
state.test = "Phase 1 complete"
state.detail = "automated stress + audible gates passed"
state.result = "PASS"
render({"PHASE 1 PASSED", "Send /v10-phase1.log"})
log("PASS", "ALL PHASE 1 MULTISPEAKER GATES PASSED")
print("")
print("[PASS] v10 Phase 1 multispeaker stress")
print("Log: " .. LOG)
