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
  local line = ("%s %-9s %s"):format(tostring(os.epoch("utc")), kind, tostring(message))
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
    if status.state == "playing" then return status end
    sleep(0.05)
  end
  error("timed out waiting for speaker to play", 0)
end

local function assertServerPlaying(label)
  local status = speaker.audioStatus()
  if status.state == "error" then error(label .. ": speaker error: " .. tostring(status.error), 0) end
  assert(status.state == "playing", label .. ": server playback state is " .. tostring(status.state))
  return status
end

local function sourceForLocalSpeaker(snapshot)
  local found, count = nil, 0
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == "finite" then found, count = source, count + 1 end
  end
  assert(count == 1, "expected exactly one finite diagnostic source, got " .. tostring(count))
  return found
end

local function assertContinuous(source, label)
  assert(source.lastState == "playing",
    label .. ": OpenAL source state is " .. tostring(source.lastState) .. ", expected playing")
  assert((source.channelStarts or 0) == 1,
    label .. ": expected exactly one OpenAL channel start, got " .. tostring(source.channelStarts))
  assert((source.channelDetaches or 0) == 0,
    label .. ": source channel detached/restarted")
  assert((source.playingToStoppedTransitions or 0) == 0,
    label .. ": source transitioned from playing to stopped")
  assert((source.recoveryRejoins or 0) == 0,
    label .. ": source performed a recovery rejoin")
  assert((source.decoderFailures or 0) == 0,
    label .. ": decoder failure was observed")
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
  assert((source.soundPhysicsProgressiveFullRefreshes or 0) > 0,
    label .. ": HQ progressive path never completed a full 17-probe refresh")
  assert((source.soundPhysicsProgressivePaths or 0) >= 17,
    label .. ": fewer than 17 progressive paths were sampled")
  assert((source.soundPhysicsPrivateEfxApplies or 0) > 0,
    label .. ": HQ private per-source SPR filters were never applied")
  assert((source.soundPhysicsPrivateDirectFilter or 0) > 0,
    label .. ": HQ private direct filter was not created")
  assert((source.soundPhysicsPrivateEfxFallbacks or 0) == 0,
    label .. ": HQ private EFX path fell back to SPR shared filters")
end

local function logLifecycle(kind, source)
  log(kind, ("source=%s group=%s state=%s starts=%d decoderRestarts=%d detaches=%d rejoins=%d stopTransitions=%d eof=%d"):format(
    tostring(source.source), tostring(source.group), tostring(source.lastState),
    source.channelStarts or 0,
    source.decoderRestarts or 0,
    source.channelDetaches or 0,
    source.recoveryRejoins or 0,
    source.playingToStoppedTransitions or 0,
    source.eofCount or 0))
end

local function logSpr(kind, source)
  log(kind, ("gain=%.4f HF=%.4f calls=%d samples=%d progressive=%d full=%d partial=%d paths=%d raw=%.4f efx=%d fallback=%d filter=%d reflected=%d"):format(
    source.directGain or -1,
    source.directGainHF or -1,
    source.soundPhysicsProcessCalls or 0,
    source.soundPhysicsSamples or 0,
    source.soundPhysicsProgressiveCalls or 0,
    source.soundPhysicsProgressiveFullRefreshes or 0,
    source.soundPhysicsProgressivePartialRefreshes or 0,
    source.soundPhysicsProgressivePaths or 0,
    source.soundPhysicsProgressiveRawOcclusion or -1,
    source.soundPhysicsPrivateEfxApplies or 0,
    source.soundPhysicsPrivateEfxFallbacks or 0,
    source.soundPhysicsPrivateDirectFilter or 0,
    source.soundPhysicsReflectionStabilizedCalls or 0))
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
    "Stand on the OPEN side with clear line of sight to the speaker.",
    "After the open sample, the screen will say MOVE NOW.",
    "Move behind the wall immediately and stay there; there is no second ENTER prompt.",
    "Press ENTER when ready to start.",
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
  assertServerPlaying("open air")
  local openSnap = speaker.hqDiagSnapshot()
  assert((openSnap.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered was not detected")
  assert((openSnap.soundEngineReloads or 0) == 0, "sound engine reloaded during open sample")
  local open = sourceForLocalSpeaker(openSnap)
  assertContinuous(open, "open air")
  assertSpr(open, "open air")
  local openCalls = open.soundPhysicsProcessCalls or 0
  assert(openCalls >= 2, "HQ live refresh did not re-run SPR while Update Moving Sounds was off")

  logLifecycle("OPEN-ID", open)
  logSpr("OPEN-SPR", open)

  prompt({
    "",
    "================ MOVE NOW ================",
    "Walk behind the solid wall immediately and stay there.",
    "The SAME MP3 must keep playing. Do not touch the computer/speaker.",
    "Sampling the wall state in 8 seconds...",
    "==========================================",
  })

  sleep(8)
  assertServerPlaying("wall")
  local wallSnap = speaker.hqDiagSnapshot()
  assert((wallSnap.soundEngineReloads or 0) == 0, "sound engine reloaded during C2")
  local wall = sourceForLocalSpeaker(wallSnap)
  assertContinuous(wall, "wall")
  assertSpr(wall, "wall")

  logLifecycle("WALL-ID", wall)
  logSpr("WALL-SPR", wall)

  assert(wall.source == open.source,
    "source ID changed during C2: " .. tostring(open.source) .. " -> " .. tostring(wall.source))
  assert(wall.group == open.group,
    "finite playback group changed during C2")
  assert((wall.channelStarts or 0) == (open.channelStarts or 0),
    "OpenAL channel restarted between open and wall samples")
  assert((wall.decoderRestarts or 0) == (open.decoderRestarts or 0),
    "finite decoder restarted between open and wall samples")
  assert((wall.channelDetaches or 0) == (open.channelDetaches or 0),
    "source channel detached between open and wall samples")
  assert((wall.recoveryRejoins or 0) == (open.recoveryRejoins or 0),
    "source recovery/rejoin occurred between open and wall samples")
  assert((wall.eofCount or 0) == (open.eofCount or 0),
    "MP3 reached EOF/looped between open and wall samples")
  assert((wall.playingToStoppedTransitions or 0) == (open.playingToStoppedTransitions or 0),
    "source stopped between open and wall samples")
  assert((wall.soundPhysicsPrivateDirectFilter or 0) == (open.soundPhysicsPrivateDirectFilter or 0),
    "private direct filter changed during the same source lifetime")

  local wallCalls = wall.soundPhysicsProcessCalls or 0
  assert(wallCalls > openCalls, "SPR did not reprocess the already-playing HQ source after movement")
  assert((wall.soundPhysicsSamples or 0) > (open.soundPhysicsSamples or 0),
    "SPR environment writes did not progress after movement")
  assert((wall.soundPhysicsProgressiveCalls or 0) > (open.soundPhysicsProgressiveCalls or 0),
    "HQ progressive direct-occlusion calls did not progress after movement")
  assert((wall.soundPhysicsProgressivePaths or 0) > (open.soundPhysicsProgressivePaths or 0),
    "HQ progressive probe count did not progress after movement")
  assert((wall.soundPhysicsPrivateEfxApplies or 0) > (open.soundPhysicsPrivateEfxApplies or 0),
    "private EFX was not reapplied after movement")

  local gainDrop = (open.directGain or 1) - (wall.directGain or 1)
  local hfDrop = (open.directGainHF or 1) - (wall.directGainHF or 1)
  local rawRise = (wall.soundPhysicsProgressiveRawOcclusion or 0)
    - (open.soundPhysicsProgressiveRawOcclusion or 0)

  log("CONTINUITY", ("sourceSame=true filterSame=true starts=%d decoderRestarts=%d detaches=%d rejoins=%d stopTransitions=%d eof=%d"):format(
    wall.channelStarts or 0,
    wall.decoderRestarts or 0,
    wall.channelDetaches or 0,
    wall.recoveryRejoins or 0,
    wall.playingToStoppedTransitions or 0,
    wall.eofCount or 0))
  log("DROP", ("open->wall gain=%.4f HF=%.4f rawOcclusionRise=%.4f"):format(
    gainDrop, hfDrop, rawRise))

  assert(gainDrop > 0.01 or hfDrop > 0.01,
    "already-playing HQ audio did not become measurably occluded behind the wall")

  log("PASS", "C2 same-source SPR refresh + progressive occlusion + private EFX passed with Update Moving Sounds OFF")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
