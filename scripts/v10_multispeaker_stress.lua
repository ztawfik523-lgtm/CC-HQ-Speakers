-- Frozen v10 multispeaker/control stress.
-- Usage: v10_multispeaker_stress <mp3> [cycles]
-- Requires at least 2 speakers attached to the same computer.
--
-- Concurrent controls may legitimately return false when superseded by a newer
-- command revision. The invariant is no deadlock/partial corruption and a final
-- deterministic command which always reaches the requested state.

local args = {...}
assert(args[1], "usage: v10_multispeaker_stress <mp3> [cycles]")
local cycles = tonumber(args[2]) or 8
assert(cycles >= 1 and cycles <= 50, "cycles must be 1..50")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local count = speaker.getSpeakerCount()
assert(count >= 2, "attach at least two speakers to this computer")

local h = assert(fs.open(args[1], "rb"), "cannot open " .. args[1])
local mp3 = h.readAll()
h.close()

local function waitAt(index, wanted, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local s = speaker.audioStatusAt(index)
    if s.state == wanted then return s end
    if s.state == "error" then error("endpoint " .. index .. " error: " .. tostring(s.error)) end
    sleep(0.03)
  end
  error(("timeout endpoint %d -> %s"):format(index, wanted))
end

local function verifyShared()
  local first = waitAt(1, "playing", 15)
  local id = first.playbackId
  for i = 2, count do
    local s = waitAt(i, "playing", 15)
    if id and s.playbackId then assert(s.playbackId == id, "playbackId mismatch at endpoint " .. i) end
  end
  return first
end

print(("v10 multispeaker stress: %d speakers, %d cycles"):format(count, cycles))

for cycle = 1, cycles do
  speaker.audioStopAll()
  assert(speaker.speakMp3All(mp3, 0.25 + (cycle % 3) * 0.05), "group start rejected at cycle " .. cycle)
  local status = verifyShared()
  local seek = math.min(0.5, math.max(0, (status.duration or 1) * 0.1))
  local concurrent = {}

  parallel.waitForAll(
    function() concurrent.seek = speaker.audioSeek(seek) end,
    function() concurrent.volume = speaker.audioSetVolumeAt(1, 0.20 + (cycle % 4) * 0.1) end,
    function() concurrent.mute = speaker.audioSetMutedAt(2, cycle % 2 == 0) end,
    function() concurrent.loop = speaker.audioSetLooping(cycle % 2 == 0) end
  )

  -- Some values may be false because a newer concurrent command superseded them.
  -- A fresh post-race command must succeed and define the final state.
  assert(speaker.audioSetLooping(false), "post-race loop stabilization failed")
  assert(speaker.audioSetMutedAt(2, false), "post-race unmute failed")
  assert(speaker.audioSetVolumeAt(1, 0.30), "post-race volume stabilization failed")

  local s1 = speaker.audioStatusAt(1)
  local s2 = speaker.audioStatusAt(2)
  assert(s1.state == "playing" or s1.state == "paused", "endpoint 1 corrupted after race")
  assert(s2.state == "playing" or s2.state == "paused", "endpoint 2 corrupted after race")

  if cycle % 2 == 0 then
    speaker.audioStopAt(2)
    waitAt(2, "idle", 5)
    local survivor = speaker.audioStatusAt(1)
    assert(survivor.state == "playing" or survivor.state == "paused",
      "endpoint-local stop killed survivor at cycle " .. cycle)
  end

  speaker.audioStopAll()
  for i = 1, count do waitAt(i, "idle", 5) end

  if cycle % 2 == 0 or cycle == cycles then
    print(("cycle %d/%d PASS (supersession allowed)"):format(cycle, cycles))
  end
end

assert(speaker.speakMp3All(mp3, 0.3), "final group start rejected")
verifyShared()
speaker.audioStopAll()
for i = 1, count do waitAt(i, "idle", 5) end

print("[PASS] v10 multispeaker/control stress")
