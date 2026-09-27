-- Standalone C2: Sound Physics proof on completely normal Minecraft ground.
-- Computer and speaker must NOT be on a Sable contraption.
-- Usage: v10_c2_spr <mp3>

local args = {...}
assert(args[1] and not args[2], "usage: v10_c2_spr <mp3>")

local MP3_PATH = args[1]
local LOG = "/v10-c2-spr.log"
local MIN_LISTEN_SECONDS = 20

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
  pcall(function() speaker.audioStop() end)
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
  error("timed out waiting for endpoint 1 to play", 0)
end

local function resetDiag(label)
  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", label .. " epoch=" .. tostring(epoch))
end

local function sourceForLocalSpeaker(snapshot)
  local found = nil
  local count = 0
  for _, source in ipairs(snapshot.sources or {}) do
    if source.kind == "finite" then
      found = source
      count = count + 1
    end
  end
  assert(count == 1,
    "expected exactly one active finite diagnostic source, got " .. tostring(count))
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
end

local function listenForAWhile(label)
  print("")
  print(label .. " is now playing and looping.")
  print("Listen and walk around if you want.")
  print("Minimum listening time: " .. MIN_LISTEN_SECONDS .. " seconds.")

  local remaining = MIN_LISTEN_SECONDS
  while remaining > 0 do
    local step = math.min(5, remaining)
    sleep(step)
    remaining = remaining - step
    if remaining > 0 then
      print(remaining .. " seconds minimum remaining...")
    end
  end

  print("Minimum complete.")
  print("Keep listening as long as you want.")
  print("Press ENTER only when you are finished with this phase.")
  waitEnter()
end

local function startLoopedMp3(label, mp3)
  resetDiag(label)
  assert(speaker.speakMp3(mp3, 0.55), label .. ": MP3 rejected")
  waitPlaying(15)
  assert(speaker.audioSetLooping(true), label .. ": could not enable looping")
end

local mp3 = readBinary(MP3_PATH)

local ok, err = pcall(function()
  speaker.hqDiagEnable(true)

  prompt({
    "C2 NORMAL-GROUND SOUND PHYSICS TEST",
    "",
    "IMPORTANT:",
    "- The COMPUTER must be placed normally in the Minecraft world.",
    "- The SPEAKER must be placed normally in the Minecraft world.",
    "- Do NOT use a Sable contraption for either block.",
    "- Use ONE speaker for this test.",
    "",
    "Prepare an open area and a solid normal-block wall nearby.",
    "For the two measurements, try to stand about the same distance from the speaker.",
    "Press ENTER when the normal-ground setup is ready.",
  })
  waitEnter()

  -- Open-air baseline.
  prompt({
    "PHASE 1: OPEN AIR",
    "Stand where there is a clear line between you and the speaker.",
    "Do not put the wall between you and the speaker yet.",
    "Press ENTER to start the open-air sound.",
  })
  waitEnter()

  startLoopedMp3("open air", mp3)
  listenForAWhile("OPEN AIR")
  local open = speaker.hqDiagSnapshot()
  assert((open.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered was not detected")
  local before = sourceForLocalSpeaker(open)
  assertSpr(before, "open air")
  log("OPEN", ("gain=%.4f HF=%.4f calls=%d"):format(
    before.directGain or -1, before.directGainHF or -1, before.soundPhysicsProcessCalls or 0))
  safeStop()

  -- Behind-wall restart.
  prompt({
    "PHASE 2: BEHIND SOLID WALL",
    "Move so the NORMAL Minecraft wall is directly between you and the speaker.",
    "Try to stay about the same distance from the speaker as in Phase 1.",
    "The sound will be RESTARTED from behind the wall.",
    "Press ENTER when you are in position.",
  })
  waitEnter()

  startLoopedMp3("wall restart", mp3)
  listenForAWhile("BEHIND WALL")
  local wall = speaker.hqDiagSnapshot()
  local after = sourceForLocalSpeaker(wall)
  assertSpr(after, "wall")
  log("WALL", ("gain=%.4f HF=%.4f calls=%d"):format(
    after.directGain or -1, after.directGainHF or -1, after.soundPhysicsProcessCalls or 0))
  safeStop()

  local gainDrop = (before.directGain or 1) - (after.directGain or 1)
  local hfDrop = (before.directGainHF or 1) - (after.directGainHF or 1)
  log("DROP", ("open->wall gain=%.4f HF=%.4f"):format(gainDrop, hfDrop))

  assert(gainDrop > 0.01 or hfDrop > 0.01,
    "SPR processed HQ audio, but the normal-world wall did not measurably increase occlusion")

  prompt({
    "PHASE 3: OPEN AIR AGAIN",
    "Move back to the OPEN side of the same wall.",
    "Keep roughly the same distance from the speaker.",
    "Press ENTER to restart the sound for the final comparison.",
  })
  waitEnter()

  startLoopedMp3("final open air", mp3)
  listenForAWhile("FINAL OPEN AIR")
  local finalOpen = speaker.hqDiagSnapshot()
  local finalSource = sourceForLocalSpeaker(finalOpen)
  assertSpr(finalSource, "final open air")
  log("FINAL", ("gain=%.4f HF=%.4f calls=%d"):format(
    finalSource.directGain or -1, finalSource.directGainHF or -1,
    finalSource.soundPhysicsProcessCalls or 0))

  local gainRecovery = (finalSource.directGain or 1) - (after.directGain or 1)
  local hfRecovery = (finalSource.directGainHF or 1) - (after.directGainHF or 1)
  log("RECOVER", ("wall->open gain=%.4f HF=%.4f"):format(gainRecovery, hfRecovery))
  assert(gainRecovery > 0.01 or hfRecovery > 0.01,
    "SPR wall occlusion did not measurably clear after returning to open air and restarting")

  log("PASS", "C2 Sound Physics normal-ground open/wall/open test")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
