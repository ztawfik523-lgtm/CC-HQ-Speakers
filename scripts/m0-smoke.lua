-- HISTORICAL: targets retired pre-v9 finite APIs (including OGG/generic whole-file playback).
-- Do not use this for current acceptance; use v9_core_acceptance.lua instead.

local RESULTS = "/m0-smoke-results.txt"

local function log(message)
  local line = ("[M0-SMOKE] %s"):format(message)
  print(line)
  local handle = fs.open(RESULTS, "a")
  if handle then
    handle.writeLine(line)
    handle.close()
  end
end

local function ask(prompt)
  write(prompt .. " ")
  return read()
end

local function observe(label)
  local result = ask(label .. " (pass/fail/notes):")
  log(label .. " => " .. result)
end

local function readBinary(path)
  local handle, reason = fs.open(path, "rb")
  if not handle then return nil, reason end
  local data = handle.readAll()
  handle.close()
  return data
end

local function sine(sampleCount, amplitude)
  local samples = {}
  for i = 1, sampleCount do
    samples[i] = math.floor(math.sin((i - 1) * 2 * math.pi * 440 / 48000) * amplitude)
  end
  return samples
end

local function playFile(speaker, method, label, path)
  if path == "" then
    log(label .. " skipped")
    return
  end
  local data, reason = readBinary(path)
  if not data then
    log(label .. " could not read " .. path .. ": " .. tostring(reason))
    return
  end
  speaker.speakStop()
  local ok, result = pcall(speaker[method], data, 1.0)
  log(label .. " call=" .. tostring(ok and result) .. " bytes=" .. #data ..
    (ok and "" or " error=" .. tostring(result)))
  if ok then observe(label .. " audible playback") end
  speaker.speakStop()
end

if fs.exists(RESULTS) then fs.delete(RESULTS) end

local speaker = peripheral.find("speaker")
if not speaker then error("No speaker peripheral is attached", 0) end
local side = peripheral.getName(speaker)

log("BEGIN peripheral=" .. tostring(side))
local methods = peripheral.getMethods(side) or {}
table.sort(methods)
log("methods=" .. table.concat(methods, ","))
log("limits maxAudio=" .. tostring(speaker.speakMaxAudioBytes()) ..
  " maxSamples=" .. tostring(speaker.speakMaxSamples()) ..
  " sampleRate=" .. tostring(speaker.speakSampleRate()))
log("supportedFiles=" .. textutils.serialize(speaker.speakSupportedFiles()))
log("speakerCount=" .. tostring(speaker.getSpeakerCount()))

local pcm8 = sine(24000, 100)
local ok8 = speaker.playAudio(pcm8, 1.0)
log("playAudio signed-8 call=" .. tostring(ok8))
sleep(1)
observe("playAudio signed-8 440 Hz tone")

local pcm16 = sine(24000, 24000)
local ok16 = speaker.speakPCM(pcm16, 1.0)
log("speakPCM signed-16 call=" .. tostring(ok16))
sleep(1)
observe("speakPCM signed-16 440 Hz tone")

speaker.speakVolume(0.25)
speaker.playAudio(pcm8)
sleep(1)
speaker.speakVolume(1.0)
speaker.playAudio(pcm8)
sleep(1)
observe("default volume changed quiet then normal")

local stopTone = sine(192000, 100)
speaker.playAudio(stopTone, 1.0)
sleep(0.5)
speaker.speakStop()
log("speakStop called; isPlaying=" .. tostring(speaker.speakIsPlaying()) ..
  " queue=" .. tostring(speaker.speakQueueSize()))
observe("stop halted the active four-second tone")

local mp3Path = ask("Small MP3 path, or blank to skip:")
playFile(speaker, "speakMp3", "MP3 finite", mp3Path)
local oggPath = ask("Small OGG path, or blank to skip:")
playFile(speaker, "speakOgg", "OGG finite", oggPath)
local wavPath = ask("Small WAV path, or blank to skip:")
playFile(speaker, "speakWav", "WAV finite", wavPath)
local genericPath = ask("Generic speakAudio path (blank reuses WAV, '-' skips):")
if genericPath == "" then genericPath = wavPath end
if genericPath ~= "-" then playFile(speaker, "speakAudio", "generic finite", genericPath) end

speaker.setLooping(true)
speaker.playAudio(pcm8, 1.0)
log("setLooping(true) then short playAudio; observe inherited baseline for 3 seconds")
sleep(3)
observe("inherited looping observation (number of repeats/what happened)")
speaker.setLooping(false)
speaker.speakStop()

local count = speaker.getSpeakerCount()
if count > 1 then
  local allOk = speaker.playAudioAll(pcm8, 1.0)
  log("playAudioAll count=" .. count .. " call=" .. tostring(allOk))
  sleep(1)
  observe("multi-speaker playAudioAll and sync/spatial result")
else
  log("multi-speaker skipped: attach at least two speakers to this computer")
end

local streamKind = ask("Stream type (1=MP3, 2=HLS, 3=MPEG-TS; blank skips):")
if streamKind ~= "" then
  local streamUrl = ask("Stream URL:")
  local method = ({
    ["1"] = "speakStream", ["2"] = "speakHLS", ["3"] = "speakTS",
    mp3 = "speakStream", hls = "speakHLS", ts = "speakTS"
  })[streamKind:lower()]
  if method then
    local ok, result = pcall(speaker[method], streamUrl, 1.0)
    log(method .. " call=" .. tostring(ok and result) .. (ok and "" or " error=" .. tostring(result)))
    sleep(12)
    log("stream active=" .. tostring(speaker.isStreaming()) ..
      " url=" .. tostring(speaker.getStreamUrl()) ..
      " metadata=" .. textutils.serialize(speaker.getStreamMeta()))
    observe("URL stream audible and metadata (if supplied by station)")
    speaker.speakStop()
  else
    log("unknown stream kind=" .. streamKind)
  end
end

log("END. Copy this file plus relevant latest.log lines into the report.")
print("Results saved to " .. RESULTS)
