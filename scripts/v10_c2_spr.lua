-- Standalone C2: ordinary-world Sound Physics proof for one HQ finite speaker.
-- Usage: v10_c2_spr <mp3>

local args = {...}
assert(args[1] and not args[2], "usage: v10_c2_spr <mp3>")

local MP3_PATH = args[1]
local LOG = "/v10-c2-spr.log"
if fs.exists(LOG) then fs.delete(LOG) end

local speaker = assert(peripheral.find("speaker"), "attach a ComputerCraft speaker")
assert(speaker.getSpeakerCount() >= 1, "at least one speaker must be attached")
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
  pcall(function() speaker.audioStopAt(1) end)
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
end

local function waitEnter()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "key" and e[2] == keys.enter then return end
  end
end

local function waitPlaying(timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local status = speaker.audioStatusAt(1)
    if status.state == "error" then error("speaker error: " .. tostring(status.error), 0) end
    if status.state == "playing" then return end
    sleep(0.05)
  end
  error("timed out waiting for endpoint 1 to play", 0)
end

local function resetDiag(label)
  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", label .. " epoch=" .. tostring(epoch))
end

local function sourceForEndpoint(snapshot)
  local p = speaker.getSpeakerPos(1)
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == "finite"
      and math.abs((source.blockX or 0) - p.x) < 0.01
      and math.abs((source.blockY or 0) - p.y) < 0.01
      and math.abs((source.blockZ or 0) - p.z) < 0.01 then
      return source
    end
  end
end

local function assertSpr(source, label)
  assert(source, label .. ": diagnostic source missing")
  assert((source.soundPhysicsProcessCalls or 0) > 0, label .. ": SPR processSound was never observed")
  assert(source.soundPhysicsProcessCategory == "block",
    label .. ": SPR used unexpected category " .. tostring(source.soundPhysicsProcessCategory))
  assert(source.soundPhysicsProcessSound == "hqspeaker:hq_audio_source",
    label .. ": SPR used unexpected sound id " .. tostring(source.soundPhysicsProcessSound))
  assert((source.soundPhysicsSamples or 0) > 0,
    label .. ": SPR processSound ran but no environment write was observed")
end

local mp3 = readBinary(MP3_PATH)

local ok, err = pcall(function()
  speaker.hqDiagEnable(true)

  print("")
  print("C2 OPEN AIR")
  print("Park the Sable contraption.")
  print("Keep endpoint 1 visible to you with NO normal-world wall between you and it.")
  print("The speaker/computer may stay on Sable.")
  print("Press ENTER when ready.")
  waitEnter()

  resetDiag("open air")
  assert(speaker.speakMp3At(1, mp3, 0.55), "open-air MP3 rejected")
  waitPlaying(15)
  sleep(3)
  local open = speaker.hqDiagSnapshot()
  assert((open.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered was not detected")
  local before = sourceForEndpoint(open)
  assertSpr(before, "open air")
  log("OPEN", ("gain=%.4f HF=%.4f calls=%d"):format(
    before.directGain or -1, before.directGainHF or -1, before.soundPhysicsProcessCalls or 0))

  safeStop()
  print("")
  print("C2 WALL")
  print("Create a SOLID NORMAL-WORLD wall between you and endpoint 1.")
  print("Leave the ship parked. Stand behind the wall, then press ENTER.")
  waitEnter()

  resetDiag("wall restart")
  assert(speaker.speakMp3At(1, mp3, 0.55), "wall MP3 rejected")
  waitPlaying(15)
  sleep(3)
  local wall = speaker.hqDiagSnapshot()
  local after = sourceForEndpoint(wall)
  assertSpr(after, "wall")
  log("WALL", ("gain=%.4f HF=%.4f calls=%d"):format(
    after.directGain or -1, after.directGainHF or -1, after.soundPhysicsProcessCalls or 0))

  local gainDrop = (before.directGain or 1) - (after.directGain or 1)
  local hfDrop = (before.directGainHF or 1) - (after.directGainHF or 1)
  log("DROP", ("gain=%.4f HF=%.4f"):format(gainDrop, hfDrop))
  assert(gainDrop > 0.01 or hfDrop > 0.01,
    "SPR processed HQ audio, but the normal-world wall did not measurably increase occlusion")

  log("PASS", "C2 Sound Physics ordinary-world startup occlusion")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
