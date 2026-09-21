-- Frozen v10 RAW/backpressure acceptance.
-- Usage: v10_raw_acceptance
-- Tests signed-16 input, bounded rejection/retry, All and At admission.

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")

assert(speaker.speakSampleRate() == 48000, "RAW sample rate changed")
assert(speaker.speakMaxSamples() == 131072, "RAW max sample count changed")

local chunk = {}
for i = 1, 24000 do
  chunk[i] = math.floor(math.sin(i * 0.055) * 22000)
end

speaker.speakStop()

local accepted = 0
local rejected = false
for i = 1, 20 do
  if speaker.speakPCM(chunk, 0.20) then
    accepted = accepted + 1
  else
    rejected = true
    break
  end
end

assert(accepted > 0, "RAW rejected the first chunk")
assert(rejected, "RAW never exercised bounded backpressure")
print("RAW accepted before rejection: " .. accepted)

local timer = os.startTimer(10)
local gotEmpty = false
while true do
  local e, a = os.pullEvent()
  if e == "hqspeaker_audio_empty" then
    gotEmpty = true
    break
  elseif e == "timer" and a == timer then
    break
  end
end
assert(gotEmpty, "timed out waiting for hqspeaker_audio_empty after observed rejection")
assert(speaker.speakPCM(chunk, 0.20), "RAW retry after hqspeaker_audio_empty was rejected")

speaker.speakStop()
sleep(0.1)

local count = speaker.getSpeakerCount()
if count >= 2 then
  local short = {}
  for i = 1, 4800 do short[i] = math.floor(math.sin(i * 0.08) * 18000) end

  assert(speaker.speakPCMAll(short, 0.18), "speakPCMAll rejected")
  sleep(0.2)
  speaker.speakStop()

  assert(speaker.speakPCMAt(2, short, 0.18), "speakPCMAt(2) rejected")
  sleep(0.1)
  speaker.audioStopAt(2)
else
  print("RAW All/At checks skipped: attach at least two speakers")
end

speaker.speakStop()
print("[PASS] frozen v10 RAW/backpressure acceptance")
