-- M1A runtime acceptance for standard CC compatibility + HQ output ownership.
-- Optional arg: a small finite MP3 path, used to verify RAW -> finite replacement.

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")

local finitePath = ...

local function waitFor(eventName, timeout)
  local timer = os.startTimer(timeout)
  while true do
    local event, a = os.pullEventRaw()
    if event == eventName then return end
    if event == "timer" and a == timer then
      error("timed out waiting for " .. eventName, 0)
    end
    if event == "terminate" then error("terminated", 0) end
  end
end

local function waitUntil(predicate, timeout, label)
  local deadline = os.clock() + timeout
  while os.clock() < deadline do
    if predicate() then return end
    sleep(0.05)
  end
  error("timed out waiting for " .. label, 0)
end

print("M1A output contract")

assert(type(speaker.stop) == "function", "native stop is missing")
assert(type(speaker.playNote) == "function", "native playNote is missing")
assert(type(speaker.playSound) == "function", "native playSound is missing")
assert(type(speaker.playAudio) == "function", "native playAudio is missing")
assert(type(speaker.speakPCM) == "function", "HQ speakPCM is missing")
assert(type(speaker.audioStatus) == "function", "HQ audioStatus is missing")
assert(speaker.speakMaxSamples() == 131072,
  "speakMaxSamples must report the real 131072-sample table limit")

speaker.stop()
sleep(0.1)

-- 0.1 s of signed 16-bit mono PCM.
local raw = {}
for i = 1, 4800 do
  raw[i] = math.floor(math.sin(i * 0.08) * 12000)
end

-- Fill the bounded inherited HQ queue until it truthfully backpressures.
local accepted = 0
local backpressured = false
for _ = 1, 64 do
  if speaker.speakPCM(raw, 0.2) then
    accepted = accepted + 1
  else
    backpressured = true
    break
  end
end
assert(backpressured, "speakPCM never reported bounded backpressure")
assert(accepted > 0, "speakPCM accepted no data")

local status = speaker.audioStatus()
assert(status.kind == "raw", "raw playback did not own audioStatus")
assert(status.canSeek == false and status.canLoop == false,
  "raw playback must not expose finite seek/loop controls")

-- Native notes remain independent while HQ continuous audio owns the main output.
assert(type(speaker.playNote("harp", 0.2, 12)) == "boolean",
  "playNote failed while HQ raw was active")

-- Native arbitrary audio must not overlap an active HQ continuous source.
local native = {}
for i = 1, 4800 do native[i] = math.floor(math.sin(i * 0.07) * 90) end
assert(speaker.playAudio(native, 0.2) == false,
  "native playAudio overlapped active HQ raw output")

-- HQ backpressure has its own event and must not borrow speaker_audio_empty semantics.
waitFor("hqspeaker_audio_empty", 5)
assert(speaker.speakPCM(raw, 0.2) == true,
  "speakPCM retry after hqspeaker_audio_empty was rejected")

if finitePath then
  local handle = assert(fs.open(finitePath, "rb"), "cannot open finite fixture: " .. finitePath)
  local bytes = handle.readAll()
  handle.close()
  assert(#bytes > 0, "finite fixture is empty")

  -- A new incompatible HQ source replaces RAW. There is no Java-side playlist.
  assert(speaker.speakMp3(bytes, 0.2) == true,
    "finite HQ source did not replace RAW")
  local finite = speaker.audioStatus()
  assert(finite.kind ~= "raw", "RAW status still owned controls after finite replacement")
  speaker.speakStop()
else
  -- Without a finite fixture, verify RAW eventually drains and releases HQ ownership.
  waitUntil(function() return not speaker.speakIsPlaying() end, 6, "RAW idle cleanup")
end

speaker.speakStop()
sleep(0.1)

-- Once HQ ownership is released, standard CC audio works normally again.
assert(speaker.playAudio(native, 0.2) == true,
  "native playAudio did not recover after HQ stop")
speaker.stop()

print("M1A output contract passed")
