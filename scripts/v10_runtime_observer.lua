-- v10 manual-runtime observer.
-- Usage: v10_runtime_observer [seconds]
-- Run while performing walk-in/out, F3+T/resource reload, Sable/VS2 movement,
-- radio membership, or other manual phases. It logs state changes and HQ events.

local seconds = tonumber(({...})[1]) or 60
assert(seconds > 0 and seconds <= 1800, "seconds must be 1..1800")

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")

print("v10 runtime observer")
print("speaker count: " .. speaker.getSpeakerCount())
print("speakers: " .. textutils.serialize(speaker.getSpeakers(), {compact = true}))
print("observing for " .. seconds .. " seconds")

local deadline = os.epoch("utc") + seconds * 1000
local lastStatus = ""

while os.epoch("utc") < deadline do
  local status = speaker.audioStatus()
  local encoded = textutils.serialize(status, {compact = true})
  if encoded ~= lastStatus then
    print(("[%d] status %s"):format(os.epoch("utc"), encoded))
    lastStatus = encoded
  end

  local timer = os.startTimer(0.25)
  while true do
    local e = {os.pullEventRaw()}
    if e[1] == "timer" and e[2] == timer then break end
    if e[1] == "terminate" then error("terminated", 0) end
    if e[1] == "hqspeaker_audio_state"
      or e[1] == "hqspeaker_audio_empty"
      or e[1] == "hqspeaker_metadata"
      or e[1] == "speaker_audio_empty"
      or e[1] == "peripheral"
      or e[1] == "peripheral_detach" then
      print(("[%d] event %s"):format(os.epoch("utc"),
        textutils.serialize(e, {compact = true})))
    end
  end
end

print("[PASS] observer completed; inspect output for unexpected error/idle transitions.")
