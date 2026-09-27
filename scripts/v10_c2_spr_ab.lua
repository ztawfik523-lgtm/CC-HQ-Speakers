-- C2 SPR A/B comparison: real Minecraft BLOCKS sound (piston) vs HQ MP3.
-- Entire setup must be on normal Minecraft ground, not Sable.
--
-- Setup:
--   1. One normal CC computer with one normal CC speaker attached.
--   2. A piston adjacent to the computer on the chosen redstone side.
--   3. Leave space in front of the piston so it can extend/retract.
--   4. A solid normal-world wall for the behind-wall phases.
--
-- Usage:
--   v10_c2_spr_ab <mp3> [redstone-side]
-- Example:
--   v10_c2_spr_ab /cchq-speaker-runtime-test-48k-mono.mp3 left

local args = {...}
assert(args[1] and not args[3], "usage: v10_c2_spr_ab <mp3> [redstone-side]")

local MP3_PATH = args[1]
local PISTON_SIDE = args[2] or "left"
local LOG = "/v10-c2-spr-ab.log"
local MIN_LISTEN_SECONDS = 20

local validSides = {
  top = true, bottom = true, left = true, right = true, front = true, back = true,
}
assert(validSides[PISTON_SIDE], "invalid redstone side: " .. tostring(PISTON_SIDE))

if fs.exists(LOG) then fs.delete(LOG) end

local speaker = assert(peripheral.find("speaker"), "attach one ComputerCraft speaker")

local function log(kind, message)
  local line = ("%-9s %s"):format(kind, tostring(message))
  print(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
end

local function prompt(lines)
  print("")
  for _, line in ipairs(lines) do print(line) end
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  return data
end

local function waitEnter()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "key" and e[2] == keys.enter then return end
  end
end

local function askYesNoUnknown(question)
  while true do
    print("")
    print(question)
    write("[y]es / [n]o / [u]nsure: ")
    local answer = (read() or ""):lower()
    if answer == "y" or answer == "yes" then return "yes" end
    if answer == "n" or answer == "no" then return "no" end
    if answer == "u" or answer == "unsure" or answer == "unknown" then return "unsure" end
    print("Please enter y, n, or u.")
  end
end

local function safeStop()
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.stop() end)
end

local function waitPlaying(timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local status = speaker.audioStatus()
    if status.state == "error" then error("HQ speaker error: " .. tostring(status.error), 0) end
    if status.state == "playing" then return end
    sleep(0.05)
  end
  error("timed out waiting for HQ MP3 to play", 0)
end

local function pulseOnce()
  redstone.setOutput(PISTON_SIDE, true)
  sleep(0.35)
  redstone.setOutput(PISTON_SIDE, false)
  sleep(0.85)
end

local function pulseFor(seconds)
  local deadline = os.epoch("utc") + seconds * 1000
  while os.epoch("utc") < deadline do pulseOnce() end
end

local function pulseForever()
  while true do pulseOnce() end
end

local function listenPiston(label)
  safeStop()
  redstone.setOutput(PISTON_SIDE, false)
  log("PHASE", label .. " piston start")

  print("")
  print(label .. " -- NORMAL MINECRAFT PISTON")
  print("The piston will extend/retract repeatedly.")
  print("Listen from the requested position.")
  print("Minimum listening time: " .. MIN_LISTEN_SECONDS .. " seconds.")

  pulseFor(MIN_LISTEN_SECONDS)

  print("Minimum complete.")
  print("It will keep pulsing. Press ENTER when you are finished listening.")
  parallel.waitForAny(pulseForever, waitEnter)
  redstone.setOutput(PISTON_SIDE, false)
  log("PHASE", label .. " piston end")
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
    "expected exactly one active finite HQ diagnostic source, got " .. tostring(count))
  return found
end

local function assertSpr(source, label)
  assert(source, label .. ": HQ diagnostic source missing")
  assert((source.soundPhysicsProcessCalls or 0) > 0,
    label .. ": SPR processSound was never observed for HQ")
  assert(source.soundPhysicsProcessCategory == "block",
    label .. ": HQ entered SPR as unexpected category " .. tostring(source.soundPhysicsProcessCategory))
  assert(source.soundPhysicsProcessSound == "hqspeaker:hq_audio_source",
    label .. ": unexpected HQ SPR sound id " .. tostring(source.soundPhysicsProcessSound))
  assert((source.soundPhysicsSamples or 0) > 0,
    label .. ": SPR processSound ran for HQ but no environment write was observed")
end

local function listenHq(label, mp3)
  resetDiag(label)
  assert(speaker.speakMp3(mp3, 0.55), label .. ": HQ MP3 rejected")
  waitPlaying(15)
  assert(speaker.audioSetLooping(true), label .. ": could not enable HQ looping")
  log("PHASE", label .. " HQ start")

  print("")
  print(label .. " -- HQ MP3")
  print("The MP3 is now looping.")
  print("Listen from the SAME position used for the piston.")
  print("Minimum listening time: " .. MIN_LISTEN_SECONDS .. " seconds.")

  sleep(MIN_LISTEN_SECONDS)

  print("Minimum complete.")
  print("It will keep playing. Press ENTER when you are finished listening.")
  waitEnter()

  local snap = speaker.hqDiagSnapshot()
  local source = sourceForLocalSpeaker(snap)
  assertSpr(source, label)
  log("HQ", ("%s gain=%.4f HF=%.4f calls=%d"):format(
    label,
    source.directGain or -1,
    source.directGainHF or -1,
    source.soundPhysicsProcessCalls or 0))

  safeStop()
  log("PHASE", label .. " HQ end")
  return source
end

local mp3 = readBinary(MP3_PATH)

local ok, err = pcall(function()
  assert(type(speaker.hqDiagEnable) == "function" and type(speaker.hqDiagSnapshot) == "function",
    "this JAR does not expose the required HQ diagnostics")

  speaker.hqDiagEnable(true)
  redstone.setOutput(PISTON_SIDE, false)

  prompt({
    "C2 SOUND PHYSICS A/B COMPARISON",
    "",
    "NORMAL GROUND ONLY. Do not use Sable for this setup.",
    "",
    "Hardware:",
    "- one normal CC computer",
    "- one normal CC speaker",
    "- one piston on the computer's " .. PISTON_SIDE .. " side",
    "- free space in front of the piston",
    "- one solid normal-world wall",
    "",
    "Before continuing, open Sound Physics settings and enable:",
    "- Debug Logging",
    "- Occlusion Logging",
    "- Environment Logging",
    "",
    "Those logs let us see exactly what SPR calculates for the piston.",
    "Press ENTER when the setup and logging are ready.",
  })
  waitEnter()

  prompt({
    "OPEN-AIR POSITION",
    "Stand with a clear line of sight to BOTH the piston and speaker.",
    "Pick a comfortable distance and stay in roughly the same place",
    "for both open-air sounds.",
    "Press ENTER when ready.",
  })
  waitEnter()

  listenPiston("OPEN AIR")
  local openHq = listenHq("OPEN AIR", mp3)

  prompt({
    "BEHIND-WALL POSITION",
    "Move so the SAME solid wall is directly between you and BOTH sources.",
    "Try to keep roughly the same source distance as open air.",
    "Do not change the piston, speaker, computer, or wall.",
    "Press ENTER when ready.",
  })
  waitEnter()

  listenPiston("BEHIND WALL")
  local wallHq = listenHq("BEHIND WALL", mp3)

  local gainDrop = (openHq.directGain or 1) - (wallHq.directGain or 1)
  local hfDrop = (openHq.directGainHF or 1) - (wallHq.directGainHF or 1)
  log("HQDROP", ("open->wall gain=%.4f HF=%.4f"):format(gainDrop, hfDrop))

  local pistonMuffled = askYesNoUnknown(
    "Was the NORMAL PISTON clearly more muffled/occluded behind the wall than in open air?")
  local hqMuffled = askYesNoUnknown(
    "Was the HQ MP3 clearly more muffled/occluded behind the wall than in open air?")

  log("LISTEN", "piston_wall_occlusion=" .. pistonMuffled)
  log("LISTEN", "hq_wall_occlusion=" .. hqMuffled)

  if pistonMuffled == "yes" and hqMuffled == "no" then
    log("RESULT", "normal BLOCKS sound occludes but HQ does not -> HQ/SPR compatibility issue strongly indicated")
  elseif pistonMuffled == "no" then
    log("RESULT", "normal piston did not occlude -> cannot blame HQ; inspect SPR/wall/setup first")
  elseif pistonMuffled == "yes" and hqMuffled == "yes" then
    log("RESULT", "both audibly occlude -> HQ playback works; compare SPR/HQ diagnostic logging")
  else
    log("RESULT", "listening result inconclusive -> use SPR debug/occlusion/environment logs")
  end

  log("PASS", "C2 SPR A/B comparison completed")
end)

redstone.setOutput(PISTON_SIDE, false)
safeStop()
pcall(function() speaker.hqDiagEnable(false) end)

if not ok then
  log("FAIL", err)
  error(err, 0)
end
