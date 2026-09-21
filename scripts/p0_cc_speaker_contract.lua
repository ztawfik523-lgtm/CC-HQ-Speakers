-- Frozen-v10 runtime acceptance for the standard CC:T speaker contract.
-- This intentionally tests native CC:T semantics, not HQ replacements.

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")

local function assertBool(value, label)
  assert(type(value) == "boolean", label .. " must return a boolean")
  return value
end

local function countIdleEmptyEvents(seconds)
  local timer = os.startTimer(seconds)
  local count = 0
  while true do
    local event, a = os.pullEventRaw()
    if event == "speaker_audio_empty" then
      count = count + 1
    elseif event == "timer" and a == timer then
      return count
    elseif event == "terminate" then
      error("terminated", 0)
    end
  end
end

print("P0 CC:T speaker compatibility")

local idleEvents = countIdleEmptyEvents(0.35)
assert(idleEvents == 0, "idle speaker spammed speaker_audio_empty (" .. idleEvents .. " events)")
assert(type(speaker.stop) == "function", "standard speaker.stop() is missing")

assertBool(speaker.playNote("harp"), "playNote")
sleep(0.1)
assertBool(speaker.playSound("minecraft:entity.experience_orb.pickup", 0.25, 1.0), "playSound")
sleep(0.2)

speaker.stop()
sleep(0.05)

local audio = {}
for i = 1, 4800 do audio[i] = math.floor(math.sin(i * 0.08) * 100) end

assert(speaker.playAudio(audio, 0.35) == true, "first playAudio buffer was rejected")
assert(speaker.playAudio(audio) == false, "second immediate playAudio should be backpressured")

local timer = os.startTimer(5)
while true do
  local event, a = os.pullEvent()
  if event == "speaker_audio_empty" then break end
  if event == "timer" and a == timer then error("timed out waiting for speaker_audio_empty") end
end

assert(speaker.playAudio(audio) == true, "playAudio retry after speaker_audio_empty was rejected")
speaker.stop()
print("[PASS] P0 standard CC:T speaker contract")
