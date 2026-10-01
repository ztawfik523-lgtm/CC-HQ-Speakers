-- CC:HQ Speakers v11 integrated runtime test 1.
-- Covers: tuning/status, Lua rejection, endpoint-local controls, live config reload,
-- simultaneous SPR private-filter isolation, F3+T recovery, and >60-block SPR playback.
-- Requires exactly 2 normal-world speakers attached to this computer.
-- Usage: v11_runtime_1 <mp3>

local args = {...}
assert(args[1] and not args[2], "usage: v11_runtime_1 <mp3>")

local MP3_PATH = args[1]
local LOG = "/v11-runtime-1.log"
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
    error(("test 1 requires exactly %d HQ-grouped speakers; HQ count=%d members=%s"):format(
      expected, count, ok and members or "<unavailable>"), 0)
  end
  return count
end

waitSpeakerCount(2, 5)
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

local function waitEnterOrTerminate()
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then return false end
    if e[1] == "key" and e[2] == keys.enter then return true end
  end
end

local function waitRequiredEnter(reason)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "terminate" then
      print("")
      print(reason)
      print("Press ENTER after the required restore is complete.")
    elseif e[1] == "key" and e[2] == keys.enter then
      return
    end
  end
end

local function prompt(lines)
  print("")
  for _, line in ipairs(lines) do print(line) end
end

local function approx(actual, expected, label, eps)
  eps = eps or EPS
  assert(type(actual) == "number", label .. " is not numeric: " .. tostring(actual))
  assert(math.abs(actual - expected) <= eps,
    ("%s expected %.4f, got %.4f"):format(label, expected, actual))
end

local function waitStatus(index, wanted, timeout, label)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local s = speaker.audioStatusAt(index)
    if s.state == "error" then error((label or "audio") .. ": " .. tostring(s.error), 0) end
    if s.state == wanted then return s end
    sleep(0.05)
  end
  error((label or ("endpoint " .. index)) .. " did not reach " .. wanted, 0)
end

local function assertTuning(status, volume, gain, range, mode, label)
  assert(status.state == "playing" or status.state == "paused", label .. ": unexpected state " .. tostring(status.state))
  approx(status.volume, volume, label .. " volume")
  approx(status.gain, gain, label .. " gain")
  approx(status.range, range, label .. " range", 0.02)
  assert(status.rangeMode == mode, label .. ": expected rangeMode=" .. mode .. ", got " .. tostring(status.rangeMode))
end

local function expectError(label, fn)
  local ok, err = pcall(fn)
  assert(not ok, label .. " was unexpectedly accepted")
  log("REJECT", label .. " -> " .. tostring(err))
end

local function diagReset(label)
  safeStop()
  sleep(0.25)
  local epoch = speaker.hqDiagReset()
  log("DIAG", label .. " epoch=" .. tostring(epoch))
  return epoch
end

local function diagSnapshot(label, settle)
  if settle and settle > 0 then sleep(settle) end
  local snap = speaker.hqDiagSnapshot()
  log("DIAG", label .. " captured")
  return snap
end

local function sourceById(snapshot, id)
  for _, source in ipairs(snapshot.sources or {}) do
    if source.source == id then return source end
  end
end

local function sourceForEndpoint(snapshot, index, kind)
  local p = speaker.getSpeakerPos(index)
  for _, source in ipairs(snapshot.sources or {}) do
    if (not kind or source.kind == kind)
      and math.abs((source.blockX or 0) - p.x) < 0.01
      and math.abs((source.blockY or 0) - p.y) < 0.01
      and math.abs((source.blockZ or 0) - p.z) < 0.01 then
      return source
    end
  end
end

local function assertSourceHealthy(source, label)
  assert(source, label .. ": diagnostic source missing")
  assert((source.channelStarts or 0) >= 1, label .. ": no real client channel")
  assert((source.playingSamples or 0) >= 3, label .. ": insufficient PLAYING samples")
  assert((source.decoderFailures or 0) == 0, label .. ": decoder failure observed")
end

local function assertPrivateSpr(source, label)
  assertSourceHealthy(source, label)
  assert((source.soundPhysicsProcessCalls or 0) > 0, label .. ": SPR processSound missing")
  assert(source.soundPhysicsProcessCategory == "block", label .. ": unexpected SPR category")
  assert(source.soundPhysicsProcessSound == "hqspeaker:hq_audio_source", label .. ": unexpected SPR sound id")
  assert((source.soundPhysicsSamples or 0) > 0, label .. ": SPR environment samples missing")
  assert((source.soundPhysicsProgressiveCalls or 0) > 0, label .. ": progressive occlusion missing")
  assert((source.soundPhysicsProgressiveFullRefreshes or 0) > 0, label .. ": no full progressive refresh")
  assert((source.soundPhysicsPrivateEfxApplies or 0) > 0, label .. ": private EFX never applied")
  assert((source.soundPhysicsPrivateDirectFilter or 0) > 0, label .. ": private direct filter missing")
  assert((source.soundPhysicsPrivateEfxFallbacks or 0) == 0, label .. ": private EFX fell back to SPR shared filters")
end

local function run(name, fn)
  log("BEGIN", name)
  local ok, err = pcall(fn)
  if not ok then
    log("FAIL", name .. ": " .. tostring(err))
    pcall(function() log("DIAGFAIL", serialize(speaker.hqDiagSnapshot())) end)
    pcall(function() log("STATUS1", serialize(speaker.audioStatusAt(1))) end)
    pcall(function() log("STATUS2", serialize(speaker.audioStatusAt(2))) end)
    safeStop()
    error(err, 0)
  end
  log("PASS", name)
end

local mp3 = readBinary(MP3_PATH)
local p1 = speaker.getSpeakerPos(1)
local p2 = speaker.getSpeakerPos(2)
log("SETUP", "endpoint1=" .. serialize(p1) .. " endpoint2=" .. serialize(p2))

speaker.hqDiagEnable(true)

run("TUNING + ENDPOINT CONTROLS", function()
  diagReset("tuning")
  assert(speaker.speakMp3All(mp3, 1.75), "2-speaker MP3 start rejected")
  waitStatus(1, "playing", 15, "endpoint 1")
  waitStatus(2, "playing", 15, "endpoint 2")
  assert(speaker.audioSetLoopingAll(true), "loop enable failed")

  assertTuning(speaker.audioStatusAt(1), 1.75, 0.585, 59.0, "auto", "endpoint 1 initial")
  assertTuning(speaker.audioStatusAt(2), 1.75, 0.585, 59.0, "auto", "endpoint 2 initial")

  assert(speaker.audioSetRangeAt(1, 80), "endpoint 1 explicit range rejected")
  assertTuning(speaker.audioStatusAt(1), 1.75, 0.585, 80.0, "explicit", "endpoint 1 explicit")
  assertTuning(speaker.audioStatusAt(2), 1.75, 0.585, 59.0, "auto", "endpoint 2 untouched range")
  assert(speaker.audioSetRangeAt(1), "endpoint 1 auto-range restore rejected")
  assertTuning(speaker.audioStatusAt(1), 1.75, 0.585, 59.0, "auto", "endpoint 1 auto restored")

  expectError("volume below zero", function() speaker.audioSetVolumeAt(1, -0.01) end)
  expectError("volume above max", function() speaker.audioSetVolumeAt(1, 3.01) end)
  expectError("non-finite volume", function() speaker.audioSetVolumeAt(1, math.huge) end)
  expectError("range zero", function() speaker.audioSetRangeAt(1, 0) end)
  expectError("range above server max", function() speaker.audioSetRangeAt(1, 256.01) end)
  expectError("non-finite range", function() speaker.audioSetRangeAt(1, math.huge) end)

  sleep(1.5)
  local before = diagSnapshot("endpoint controls baseline")
  local s1Before = assert(sourceForEndpoint(before, 1, "finite"), "endpoint 1 source missing")
  local s2Before = assert(sourceForEndpoint(before, 2, "finite"), "endpoint 2 source missing")
  assertSourceHealthy(s1Before, "endpoint 1 baseline")
  assertSourceHealthy(s2Before, "endpoint 2 baseline")
  local gain1 = s1Before.sourceGain or 0
  local gain2 = s2Before.sourceGain or 0
  assert(gain1 > 0.0001 and gain2 > 0.0001, "Minecraft Master/Blocks volume appears muted")

  assert(speaker.audioSetVolumeAt(1, 1.0), "endpoint 1 volume update rejected")
  assertTuning(speaker.audioStatusAt(1), 1.0, 0.34, 29.0, "auto", "endpoint 1 lowered")
  assertTuning(speaker.audioStatusAt(2), 1.75, 0.585, 59.0, "auto", "endpoint 2 unaffected")
  sleep(0.75)
  local quieter = diagSnapshot("endpoint 1 lowered")
  local q1 = assert(sourceById(quieter, s1Before.source), "endpoint 1 source disappeared")
  local q2 = assert(sourceById(quieter, s2Before.source), "endpoint 2 source disappeared")
  assert((q1.sourceGain or gain1) < gain1 * 0.72,
    ("endpoint 1 real OpenAL gain did not fall enough: %.4f -> %.4f"):format(gain1, q1.sourceGain or -1))
  assert((q2.sourceGain or 0) > gain2 * 0.90, "endpoint 2 OpenAL gain changed with endpoint 1")
  assert(speaker.audioSetVolumeAt(1, 1.75), "endpoint 1 volume restore rejected")
  sleep(0.5)

  local muteBase = diagSnapshot("pre-mute")
  local m1 = assert(sourceById(muteBase, s1Before.source), "endpoint 1 pre-mute source missing")
  local m2 = assert(sourceById(muteBase, s2Before.source), "endpoint 2 pre-mute source missing")
  assert(speaker.audioSetMutedAt(1, true), "endpoint 1 mute rejected")
  assert(speaker.audioStatusAt(1).muted == true, "endpoint 1 mute state missing")
  assert(speaker.audioStatusAt(2).muted == false, "endpoint 2 was muted by endpoint 1")
  sleep(0.75)
  local muted = diagSnapshot("muted")
  local m1Muted = assert(sourceById(muted, s1Before.source), "endpoint 1 mute history missing")
  local m2Muted = assert(sourceById(muted, s2Before.source), "endpoint 2 history missing")
  assert((m1Muted.channelDetaches or 0) > (m1.channelDetaches or 0), "endpoint 1 mute did not hibernate renderer")
  assert((m2Muted.channelDetaches or 0) == (m2.channelDetaches or 0), "endpoint 1 mute detached endpoint 2")

  assert(speaker.audioSetMutedAt(1, false), "endpoint 1 unmute rejected")
  waitStatus(1, "playing", 15, "endpoint 1 unmute")
  sleep(1.0)
  local unmuted = diagSnapshot("unmuted")
  local m1Unmuted = assert(sourceById(unmuted, s1Before.source), "endpoint 1 unmute history missing")
  assert((m1Unmuted.channelStarts or 0) > (m1.channelStarts or 0), "endpoint 1 renderer was not recreated after unmute")
  assert((m1Unmuted.decoderFailures or 0) == 0, "decoder failure during mute/unmute")

  assert(speaker.audioPauseAll(), "group pause rejected")
  waitStatus(1, "paused", 5, "endpoint 1 pause")
  waitStatus(2, "paused", 5, "endpoint 2 pause")
  sleep(0.5)
  local paused = diagSnapshot("paused")
  for i = 1, 2 do
    local src = assert(sourceForEndpoint(paused, i, "finite"), "paused endpoint source missing")
    assert((src.pausedSamples or 0) > 0, "endpoint " .. i .. " was never observed PAUSED by OpenAL")
  end
  assert(speaker.audioResumeAll(), "group resume rejected")
  waitStatus(1, "playing", 10, "endpoint 1 resume")
  waitStatus(2, "playing", 10, "endpoint 2 resume")
end)

run("LIVE CONFIG RELOAD", function()
  diagReset("config reload")
  assert(speaker.speakMp3At(1, mp3, 1.5), "config baseline endpoint 1 start rejected")
  local old = waitStatus(1, "playing", 15, "config baseline endpoint 1")
  assert(speaker.audioSetLoopingAt(1, true), "config baseline loop enable failed")
  assertTuning(old, 1.5, 0.50, 48.0, "auto", "config A endpoint 1")

  prompt({
    "LIVE CONFIG RELOAD -- CHANGE ONE VALUE",
    "Edit this world's server config: saves/<world>/serverconfig/hqspeaker-server.toml",
    "Under [audio.gain], change:",
    "    at1_5 = 0.50",
    "to:",
    "    at1_5 = 0.40",
    "Save the file. Do NOT restart Minecraft or the computer.",
    "Then return here and press ENTER.",
  })
  local proceed = waitEnterOrTerminate()
  if not proceed then
    prompt({
      "SAFE ABORT -- RESTORE CONFIG BEFORE EXITING",
      "Set [audio.gain] at1_5 to 0.50 (or leave it there if you had not changed it).",
      "Save the file, then press ENTER. Ctrl+T is ignored until this restore step is acknowledged.",
    })
    waitRequiredEnter("The server config may have been modified and must be restored first.")
    sleep(4.0)
    safeStop()
    assert(speaker.speakMp3At(1, mp3, 1.5), "safe-abort restore verification start rejected")
    local abortRestored = waitStatus(1, "playing", 15, "safe-abort restore verification")
    assertTuning(abortRestored, 1.5, 0.50, 48.0, "auto", "safe-abort restored config")
    safeStop()
    error("terminated", 0)
  end

  local phaseOk, phaseErr = pcall(function()
    sleep(4.0)
    local stillOld = speaker.audioStatusAt(1)
    assertTuning(stillOld, 1.5, 0.50, 48.0, "auto", "already-running source after reload")
    assert(speaker.speakMp3At(2, mp3, 1.5), "new endpoint 2 source rejected after reload")
    local fresh = waitStatus(2, "playing", 15, "new endpoint 2 source")
    assert(speaker.audioSetLoopingAt(2, true), "new endpoint 2 loop enable failed")
    assertTuning(fresh, 1.5, 0.40, 48.0, "auto", "new source under config B")

    sleep(1.5)
    local live = diagSnapshot("config A+B simultaneous")
    local oldClient = assert(sourceForEndpoint(live, 1, "finite"), "existing config-A client source missing")
    local newClient = assert(sourceForEndpoint(live, 2, "finite"), "new config-B client source missing")
    assertSourceHealthy(oldClient, "config-A client source")
    assertSourceHealthy(newClient, "config-B client source")
    assert((oldClient.sourceGain or 0) > 0.0001 and (newClient.sourceGain or 0) > 0.0001,
      "cannot compare config gains because Minecraft Master/Blocks volume is muted")
    assert((newClient.sourceGain or 0) < (oldClient.sourceGain or 0) * 0.90,
      ("new source did not receive the lower config-B gain on the real OpenAL channel: %.4f vs %.4f"):format(
        newClient.sourceGain or -1, oldClient.sourceGain or -1))
    log("CONFIG", ("server old/new gain=0.50/0.40; OpenAL old/new=%.4f/%.4f"):format(
      oldClient.sourceGain or -1, newClient.sourceGain or -1))
  end)

  prompt({
    "RESTORE THE CONFIG NOW (required even if the prior check failed)",
    "Change [audio.gain] at1_5 back to:",
    "    at1_5 = 0.50",
    "Save, then press ENTER.",
  })
  waitRequiredEnter("The server config must be restored to at1_5 = 0.50 before this test can exit.")
  sleep(4.0)
  safeStop()
  assert(speaker.speakMp3At(1, mp3, 1.5), "post-restore source start rejected")
  local restored = waitStatus(1, "playing", 15, "post-restore source")
  assertTuning(restored, 1.5, 0.50, 48.0, "auto", "restored config")
  safeStop()

  if not phaseOk then error("config reload check failed before restore: " .. tostring(phaseErr), 0) end
end)

run("SIMULTANEOUS SPR ISOLATION + F3+T", function()
  p1 = speaker.getSpeakerPos(1)
  p2 = speaker.getSpeakerPos(2)
  prompt({
    "TWO-SOURCE SPR SETUP",
    ("Endpoint 1: x=%s y=%s z=%s"):format(p1.x, p1.y, p1.z),
    ("Endpoint 2: x=%s y=%s z=%s"):format(p2.x, p2.y, p2.z),
    "Stand where endpoint 1 has CLEAR line of sight and endpoint 2 is behind a SOLID normal-world wall.",
    "Sound Physics 'Update Moving Sounds' should remain OFF.",
    "Press ENTER when that geometry is ready.",
  })
  waitEnter()

  diagReset("two-source SPR")
  assert(speaker.speakMp3All(mp3, 1.5), "SPR group start rejected")
  local st1 = waitStatus(1, "playing", 15, "SPR endpoint 1")
  local st2 = waitStatus(2, "playing", 15, "SPR endpoint 2")
  assert(st1.playbackId == st2.playbackId, "SPR group playbackId mismatch")
  assert(speaker.audioSetLoopingAll(true), "SPR group loop enable failed")
  sleep(3.0)

  local baseline = diagSnapshot("SPR clear+wall")
  assert((baseline.capabilities or {}).soundPhysicsLoaded == true, "Sound Physics Remastered not detected")
  local clear = assert(sourceForEndpoint(baseline, 1, "finite"), "clear source missing")
  local wall = assert(sourceForEndpoint(baseline, 2, "finite"), "wall source missing")
  assertPrivateSpr(clear, "clear endpoint")
  assertPrivateSpr(wall, "wall endpoint")
  assert(clear.soundPhysicsPrivateDirectFilter ~= wall.soundPhysicsPrivateDirectFilter,
    "two simultaneous HQ sources share the same private direct-filter id")

  local gainGap = (clear.directGain or 1) - (wall.directGain or 1)
  local hfGap = (clear.directGainHF or 1) - (wall.directGainHF or 1)
  local rawGap = (wall.soundPhysicsProgressiveRawOcclusion or 0) - (clear.soundPhysicsProgressiveRawOcclusion or 0)
  log("SPR", ("clear filter=%s gain=%.4f HF=%.4f raw=%.4f | wall filter=%s gain=%.4f HF=%.4f raw=%.4f"):format(
    tostring(clear.soundPhysicsPrivateDirectFilter), clear.directGain or -1, clear.directGainHF or -1,
    clear.soundPhysicsProgressiveRawOcclusion or -1,
    tostring(wall.soundPhysicsPrivateDirectFilter), wall.directGain or -1, wall.directGainHF or -1,
    wall.soundPhysicsProgressiveRawOcclusion or -1))
  assert(gainGap > 0.02 or hfGap > 0.05,
    ("wall source is not measurably more muffled than clear source (gainGap=%.4f hfGap=%.4f)"):format(gainGap, hfGap))
  assert(rawGap > 0.10,
    ("wall source progressive occlusion is not higher than clear source (rawGap=%.4f)"):format(rawGap))

  local playbackId = st1.playbackId
  local reloadsBefore = baseline.soundEngineReloads or 0
  prompt({
    "F3+T RECOVERY",
    "Exit the computer GUI now.",
    "Press F3+T once and wait until the resource/sound reload finishes.",
    "Reopen this computer and press ENTER. Do not restart the script.",
  })
  waitEnter()

  local r1 = waitStatus(1, "playing", 15, "endpoint 1 after F3+T")
  local r2 = waitStatus(2, "playing", 15, "endpoint 2 after F3+T")
  assert(r1.playbackId == playbackId and r2.playbackId == playbackId, "F3+T changed server playback authority")

  local after = nil
  local recoveryDeadline = os.epoch("utc") + 15000
  while os.epoch("utc") < recoveryDeadline do
    local snap = speaker.hqDiagSnapshot()
    local ready = (snap.soundEngineReloads or 0) > reloadsBefore
    local recovered = 0
    if ready then
      for _, old in ipairs({clear, wall}) do
        local now = sourceById(snap, old.source)
        if not now
          or (now.channelStarts or 0) <= (old.channelStarts or 0)
          or (now.pcmReadBytes or 0) <= (old.pcmReadBytes or 0)
          or (now.soundPhysicsProcessCalls or 0) <= (old.soundPhysicsProcessCalls or 0)
          or (now.soundPhysicsPrivateEfxApplies or 0) <= (old.soundPhysicsPrivateEfxApplies or 0)
          or (now.decoderFailures or 0) ~= 0 then
          ready = false
          break
        end
        recovered = recovered + math.max(0, (now.recoveryRejoins or 0) - (old.recoveryRejoins or 0))
      end
      if recovered <= 0 then ready = false end
    end
    if ready then after = snap break end
    sleep(0.25)
  end
  assert(after, "F3+T recovery did not recreate channels, resume PCM, rejoin, and reapply SPR/private EFX within 15 seconds")
  log("DIAG", "after F3+T recovery captured")

  for _, old in ipairs({clear, wall}) do
    local now = assert(sourceById(after, old.source), "source history missing after F3+T")
    assert((now.channelStarts or 0) > (old.channelStarts or 0), "client channel was not recreated after F3+T")
    assert((now.pcmReadBytes or 0) > (old.pcmReadBytes or 0), "PCM did not progress after F3+T")
    assert((now.soundPhysicsProcessCalls or 0) > (old.soundPhysicsProcessCalls or 0),
      "SPR did not reprocess source after F3+T")
    assert((now.soundPhysicsPrivateEfxApplies or 0) > (old.soundPhysicsPrivateEfxApplies or 0),
      "private EFX was not reapplied after F3+T")
    assert((now.decoderFailures or 0) == 0, "decoder failure during F3+T recovery")
    assertPrivateSpr(now, "recovered source")
  end
  local c2 = assert(sourceForEndpoint(after, 1, "finite"), "recovered endpoint 1 missing")
  local w2 = assert(sourceForEndpoint(after, 2, "finite"), "recovered endpoint 2 missing")
  assert(c2.soundPhysicsPrivateDirectFilter ~= w2.soundPhysicsPrivateDirectFilter,
    "private filters were not re-isolated after F3+T")
end)

run(">60 BLOCK SPR PLAYBACK + OCCLUSION", function()
  safeStop()
  p1 = speaker.getSpeakerPos(1)
  prompt({
    "LONG-RANGE BASELINE",
    ("Endpoint 1 is at x=%s y=%s z=%s"):format(p1.x, p1.y, p1.z),
    "Stand near endpoint 1 with CLEAR line of sight.",
    "Also prepare a point 70-80 blocks from endpoint 1 where a NORMAL solid wall blocks the speaker.",
    "Use F3 coordinates so the far point is genuinely 70-80 blocks away.",
    "Press ENTER to capture the near clear baseline. Do not walk away until the next prompt.",
  })
  waitEnter()

  diagReset("long range")
  assert(speaker.speakMp3At(1, mp3, 2.5), "long-range source start rejected")
  local baseStatus = waitStatus(1, "playing", 15, "long-range endpoint")
  assert(speaker.audioSetLoopingAt(1, true), "long-range loop enable failed")
  assertTuning(baseStatus, 2.5, 0.84, 96.0, "auto", "long-range source")
  sleep(1.5)
  local near = diagSnapshot("long-range near baseline")
  local nearSource = assert(sourceForEndpoint(near, 1, "finite"), "long-range baseline source missing")
  assertPrivateSpr(nearSource, "long-range baseline")

  prompt({
    "NEAR BASELINE CAPTURED -- TIMED FAR SAMPLE",
    "When you press ENTER, EXIT the GUI immediately.",
    "Walk to the prepared 70-80 block point and stand behind the solid wall.",
    "Be there by about 24 seconds and stay there through 32 seconds.",
    "The script captures the far sample automatically at roughly 30 seconds.",
    "After 32 seconds, return to this computer and press ENTER again.",
    "Press ENTER when ready to begin the timed walk.",
  })
  waitEnter()

  log("ACTION", "timed far sample started; operator should be 70-80 blocks away behind wall by about 24s")
  sleep(30.0)
  local far = diagSnapshot("70-80 block wall sample")
  local farSource = assert(sourceById(far, nearSource.source), "long-range source disappeared before far sample")
  assert(farSource.lastState == "playing", "real client/OpenAL source was not PLAYING at the 70-80 block sample")
  assert((farSource.soundPhysicsProcessCalls or 0) > (nearSource.soundPhysicsProcessCalls or 0),
    "SPR did not continue processing the long-range source")
  assert((farSource.soundPhysicsProgressiveCalls or 0) > (nearSource.soundPhysicsProgressiveCalls or 0),
    "progressive SPR work did not continue at long range")
  assert((farSource.soundPhysicsPrivateEfxFallbacks or 0) == 0, "long-range source fell back to shared SPR EFX")

  local gainDrop = (nearSource.directGain or 1) - (farSource.directGain or 1)
  local hfDrop = (nearSource.directGainHF or 1) - (farSource.directGainHF or 1)
  local rawRise = (farSource.soundPhysicsProgressiveRawOcclusion or 0)
    - (nearSource.soundPhysicsProgressiveRawOcclusion or 0)
  log("FAR", ("state=%s calls=%d progressive=%d gainDrop=%.4f hfDrop=%.4f rawRise=%.4f"):format(
    tostring(farSource.lastState), farSource.soundPhysicsProcessCalls or 0,
    farSource.soundPhysicsProgressiveCalls or 0, gainDrop, hfDrop, rawRise))
  assert(gainDrop > 0.01 or hfDrop > 0.01,
    "70-80 block wall did not measurably increase direct occlusion")
  assert(rawRise > 0.05,
    "70-80 block wall did not increase progressive raw occlusion")

  prompt({
    "FAR SAMPLE CAPTURED.",
    "Return to the computer if you have not already, then press ENTER.",
  })
  waitEnter()
  local returned = waitStatus(1, "playing", 15, "long-range source after return")
  assert(returned.playbackId == baseStatus.playbackId, "long-range test restarted the server playback")
end)

safeStop()
pcall(function() speaker.hqDiagEnable(false) end)
log("PASS", "V11 RUNTIME TEST 1 COMPLETE")
print("")
print("[PASS] v11 runtime test 1")
print("Log: " .. LOG)