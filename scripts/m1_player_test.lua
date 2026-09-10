local args = {...}
if #args < 3 then
  error("usage: m1_player_test <mp3> <ogg> <wav>")
end

local speaker = peripheral.find("speaker")
assert(speaker, "attach an HQ speaker")

local function read(path)
  local handle = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = handle.readAll()
  handle.close()
  return data
end

local function status(label)
  local value = speaker.audioStatus()
  print(label, textutils.serialize(value, {compact = true}))
  return value
end

local function waitFor(wanted, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local value = speaker.audioStatus()
    if value.state == wanted then return value end
    sleep(0.1)
  end
  error("timed out waiting for " .. wanted)
end

local function finiteCase(name, method, data)
  speaker.audioStop()
  assert(speaker[method](data, 0.7), method .. " rejected the file")
  status(name .. " queued")
  local playing = waitFor("playing", 15)
  assert(playing.observed == true, "renderer was not observed")
  assert(type(playing.duration) == "number" and playing.duration > 0, "no duration")
  local startedAt = playing.position
  sleep(1)
  local beforePause = speaker.audioStatus().position
  assert(beforePause > startedAt + 0.5, "position did not advance while playing")
  assert(speaker.audioPause(), "pause failed")
  waitFor("paused", 5)
  sleep(1)
  local paused = speaker.audioStatus().position
  assert(math.abs(paused - beforePause) < 0.35, "position advanced while paused")
  assert(speaker.audioSeek(math.min(playing.duration * 0.5, 2)), "paused seek failed")
  sleep(0.4)
  assert(speaker.audioStatus().state == "paused", "paused seek resumed playback")
  assert(speaker.audioResume(), "resume failed")
  waitFor("playing", 5)
  assert(speaker.audioSeek(math.min(playing.duration * 0.75, 3)), "forward seek failed")
  assert(speaker.audioSetVolume(0.25), "live volume failed")
  assert(speaker.audioSeek(0), "backward seek failed")
  sleep(0.5)
  status(name .. " controls passed")
end

local mp3, ogg, wav = read(args[1]), read(args[2]), read(args[3])
finiteCase("MP3", "speakMp3", mp3)

assert(speaker.audioSetLooping(true), "loop enable failed")
local loopStatus = speaker.audioStatus()
assert(loopStatus.duration <= 8, "use an MP3 no longer than 8 seconds for the loop test")
assert(speaker.audioSeek(math.max(0, loopStatus.duration - 0.5)), "loop seek failed")
local wraps, previous = 0, speaker.audioStatus().position
local loopDeadline = os.epoch("utc") + math.ceil(loopStatus.duration * 4 + 5) * 1000
while wraps < 3 and os.epoch("utc") < loopDeadline do
  sleep(0.05)
  local current = speaker.audioStatus()
  assert(current.state == "playing", "loop stopped before three cycles")
  if current.position + 0.2 < previous then
    wraps = wraps + 1
    print("loop wrap", wraps)
  end
  previous = current.position
end
assert(wraps >= 3, "did not observe three loop cycles")
assert(speaker.audioSetLooping(false), "loop disable failed")
waitFor("ended", math.ceil(loopStatus.duration + 8))
assert(speaker.speakIsPlaying() == false, "speakIsPlaying stayed true after natural end")
status("loop and natural end passed")

finiteCase("OGG", "speakOgg", ogg)
speaker.audioSeek(math.max(0, speaker.audioStatus().duration - 0.5))
waitFor("ended", 8)
finiteCase("WAV", "speakWav", wav)
speaker.audioSeek(math.max(0, speaker.audioStatus().duration - 0.5))
waitFor("ended", 8)

speaker.audioStop()
local pcm8, pcm16 = {}, {}
for i = 1, 2400 do
  pcm8[i] = math.floor(math.sin(i * 0.08) * 100)
  pcm16[i] = math.floor(math.sin(i * 0.08) * 24000)
end
assert(speaker.playAudio(pcm8, 0.3), "playAudio failed")
sleep(0.2)
assert(speaker.speakPCM(pcm16, 0.3), "speakPCM failed")
sleep(0.2)
assert(speaker.audioSeek(1) == false, "raw PCM claimed seek support")

speaker.audioStop()
assert(speaker.speakMp3(mp3, 0.4))
assert(speaker.speakOgg(ogg, 0.4))
local first = waitFor("playing", 15)
speaker.audioSeek(math.max(0, first.duration - 0.5))
local deadline = os.epoch("utc") + 15000
local second
while os.epoch("utc") < deadline do
  local value = speaker.audioStatus()
  if value.state == "playing" and value.generation ~= first.generation then
    second = value
    break
  end
  sleep(0.1)
end
assert(second, "queued finite track did not advance generations")
print("two finite generations played in order")

speaker.audioStop()
assert(speaker.speakMp3(mp3, 0.4))
speaker.audioStop()
assert(speaker.audioStatus().state == "idle", "stop while loading did not become idle")
assert(speaker.speakMp3(mp3, 0.4))
speaker.audioStop()
assert(speaker.speakOgg(ogg, 0.4), "rapid replacement was rejected")
local replacement = waitFor("playing", 15)
assert(replacement.format == "ogg", "stale decode revived the replaced track")
speaker.audioStop()
assert(speaker.audioStatus().state == "idle", "stop while playing did not become idle")

print("Automated Lua checks passed. Complete the manual checks in M1-RUNTIME-TEST.md.")
