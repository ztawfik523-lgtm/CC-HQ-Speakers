-- Protocol v11 standalone C2: live SPR refresh + progressive HQ acoustics on normal Minecraft ground.
-- Computer and speaker must NOT be on a Sable contraption.
-- Sound Physics "Update Moving Sounds" must be OFF.
-- Usage: v11_c2_spr <mp3>

local args = {...}
assert(args[1] and not args[2], "usage: v11_c2_spr <mp3>")

local MP3_PATH = args[1]
local LOG = "/v11-c2-spr.log"

if fs.exists(LOG) then fs.delete(LOG) end

local speaker = assert(peripheral.find("speaker"), "attach one ComputerCraft speaker")
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
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.audioStopAll() end)
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

local function waitPlaying(timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local status = speaker.audioStatus()
    if status.state == "error" then error("speaker error: " .. tostring(status.error), 0) end
    if status.state == "playing" then return end
    sleep(0.05)
  end
  error("timed out waiting for speaker to play", 0)
end

local function sourceForLocalSpeaker(snapshot)
  local found, count = nil, 0
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == "finite" then found, count = source, count + 1 end
  end
  assert(count == 1, "expected exactly one active finite diagnostic source, got " .. tostring(count))
  return found
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
  assert((source.soundPhysicsProgressiveCalls or 0) > 0,
    label .. ": HQ progressive direct-occlusion path never ran")
  assert((source.soundPhysicsProgressivePaths or 0) >= 17,
    label .. ": HQ progressive path did not complete a full 17-probe refresh")
  assert((source.soundPhysicsPrivateEfxApplies or 0) > 0,
    label .. ": HQ private per-source SPR filters were never applied")
  assert((source.soundPhysicsPrivateDirectFilter or 0) > 0,
    label .. ": HQ private direct filter was not created")
end

local mp3 = readBinary(MP3_PATH)

local ok, err = pcall(function()
  speaker.hqDiagEnable(true)

  prompt({
    "C2 LIVE SOUND PHYSICS + HQ ACOUSTICS TEST",
    "",
    "- Computer and speaker: normal Minecraft ground, NOT Sable.",
    "- Use one speaker and one solid normal-block wall.",
    "- In Sound Physics, turn Update Moving Sounds OFF.",
    "",
    "Start on the OPEN side with clear line of sight to the speaker.",
    "Press ENTER when ready.",
  })
  waitEnter()

  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", "live refresh epoch=" .. tostring(epoch))

  assert(speaker.speakMp3(mp3, 1.5), "MP3 rejected")
  waitPlaying(15)
  assert(speaker.audioSetLooping(true), "could not enable looping")

  sleep(1.6)
  local openSnap = speaker.hqDiagSnapshot()
  assert((openSnap.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered was not detected")
  local open = sourceForLocalSpeaker(openSnap)
  assertSpr(open, "open air")
  local openCalls = open.soundPhysicsProcessCalls or 0
  assert(openCalls >= 2, "HQ live refresh did not re-run SPR while Update Moving Sounds was off")

  log("OPEN", ("gain=%.4f HF=%.4f calls=%d progressive=%d paths=%d raw=%.4f efx=%d filter=%d"):format(
    open.directGain or -1, open.directGainHF or -1, openCalls,
    open.soundPhysicsProgressiveCalls or 0,
    open.soundPhysicsProgressivePaths or 0,
    open.soundPhysicsProgressiveRawOcclusion or -1,
    open.soundPhysicsPrivateEfxApplies or 0,
    open.soundPhysicsPrivateDirectFilter or 0))

  prompt({
    "",
    "The SAME MP3 will keep playing. Do NOT restart it.",
    "Press ENTER, then walk behind the solid wall and stay there.",
    "The script samples automatically after 8 seconds.",
  })
  waitEnter()

  sleep(8)
  local wallSnap = speaker.hqDiagSnapshot()
  local wall = sourceForLocalSpeaker(wallSnap)
  assertSpr(wall, "live wall")
  local wallCalls = wall.soundPhysicsProcessCalls or 0

  log("WALL", ("gain=%.4f HF=%.4f calls=%d progressive=%d paths=%d raw=%.4f reflected=%d efx=%d fallback=%d"):format(
    wall.directGain or -1, wall.directGainHF or -1, wallCalls,
    wall.soundPhysicsProgressiveCalls or 0,
    wall.soundPhysicsProgressivePaths or 0,
    wall.soundPhysicsProgressiveRawOcclusion or -1,
    wall.soundPhysicsReflectionStabilizedCalls or 0,
    wall.soundPhysicsPrivateEfxApplies or 0,
    wall.soundPhysicsPrivateEfxFallbacks or 0))

  assert(wallCalls > openCalls, "SPR did not reprocess the already-playing HQ source after movement")
  assert((wall.soundPhysicsProgressiveCalls or 0) > (open.soundPhysicsProgressiveCalls or 0),
    "HQ progressive direct-occlusion path did not refresh after movement")

  local gainDrop = (open.directGain or 1) - (wall.directGain or 1)
  local hfDrop = (open.directGainHF or 1) - (wall.directGainHF or 1)
  log("DROP", ("live open->wall gain=%.4f HF=%.4f"):format(gainDrop, hfDrop))

  assert(gainDrop > 0.01 or hfDrop > 0.01,
    "already-playing HQ audio did not become measurably occluded behind the wall")

  safeStop()
  log("PASS", "C2 live SPR refresh + progressive HQ acoustics work with Update Moving Sounds OFF")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
